package com.posapp.manufacturing;
import com.posapp.common.Qty;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.manufacturing.dto.ComponentAvailabilityDto;
import com.posapp.manufacturing.dto.CreateManufacturingOrderRequest;
import com.posapp.manufacturing.dto.UpdateManufacturingOrderRequest;
import com.posapp.manufacturing.dto.ManufacturingOrderDto;
import com.posapp.manufacturing.dto.ProduceManufacturingOrderRequest;
import com.posapp.settings.StoreSettingsService;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manufacturing Module — "Manufacturing orders". A DRAFT is created against one {@link Bom},
 * {@link #confirm}ed (locking the Bom choice and snapshotting its routing), then {@link
 * #produce}d in one or more runs until {@code quantityProduced} reaches {@code quantityPlanned} —
 * see {@link ManufacturingOrder}'s own javadoc for the full lifecycle and why only {@code produce}
 * ever touches stock.
 *
 * <p>Stock rules for both components (consumed) and the finished good (produced) mirror the rest
 * of the app exactly: {@link com.posapp.catalog.Product#isTrackStock()} false means that
 * product/component is never checked or adjusted at all (Fourier: "Add track stock attribute on
 * Product" — a non-tracked component behaves the same way here as everywhere else it's checked),
 * and when tracking is on, insufficient component stock blocks the run unless the store-wide
 * {@code StoreSettings.allowNegativeStock} is on, the same "Allow negative stock" gate {@code
 * OrderService.validateStockAvailability}/{@code SalesOrderService.fulfillLines} already enforce.
 */
@Service
public class ManufacturingOrderService {

    private final ManufacturingOrderRepository repository;
    private final ManufacturingOrderRunRepository runRepository;
    private final BomService bomService;
    private final OverheadCostService overheadCostService;
    private final CatalogService catalogService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    private final AccountingService accountingService;
    /** Multi-warehouse phase 4 — per-order location for component consumption and finished-goods output. */
    private final WarehouseService warehouseService;

    public ManufacturingOrderService(ManufacturingOrderRepository repository,
                                      ManufacturingOrderRunRepository runRepository,
                                      BomService bomService,
                                      OverheadCostService overheadCostService,
                                      CatalogService catalogService,
                                      StoreSettingsService storeSettingsService,
                                      AuditLogService auditLogService,
                                      AccountingService accountingService,
                                      WarehouseService warehouseService) {
        this.repository = repository;
        this.runRepository = runRepository;
        this.bomService = bomService;
        this.overheadCostService = overheadCostService;
        this.catalogService = catalogService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
        this.accountingService = accountingService;
        this.warehouseService = warehouseService;
    }

    @Transactional(readOnly = true)
    public List<ManufacturingOrderDto> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(ManufacturingOrderDto::from).toList();
    }

    /** Orders with search (number, product, recipe, note), status, product and the day (scheduled day, else the day it was made). */
    @Transactional(readOnly = true)
    public List<ManufacturingOrderDto> filtered(String q, String status, Long productId, java.time.LocalDate from, java.time.LocalDate to) {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> com.posapp.common.ListPaging.is(status, o.getStatus().name()))
                .filter(o -> com.posapp.common.ListPaging.sameId(productId, o.getProduct() == null ? null : o.getProduct().getId()))
                .filter(o -> o.getScheduledDate() != null
                        ? com.posapp.common.ListPaging.between(o.getScheduledDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(), from, to)
                        : com.posapp.common.ListPaging.between(o.getCreatedAt(), from, to))
                .filter(o -> com.posapp.common.ListPaging.has(q, o.getOrderNumber(), o.getProduct() == null ? null : o.getProduct().getName(),
                        o.getProduct() == null ? null : o.getProduct().getSku(), o.getBom() == null ? null : o.getBom().getCode(), o.getNote()))
                .map(ManufacturingOrderDto::from).toList();
    }

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    /** Charge for {@code minutes} at an hourly rate. */
    static BigDecimal charge(BigDecimal minutes, BigDecimal hourlyRate) {
        if (minutes == null || hourlyRate == null) return BigDecimal.ZERO;
        return minutes.multiply(hourlyRate).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    /** Copies the routing's steps onto the order (once, when it is confirmed). */
    private void snapshotOperations(ManufacturingOrder mo) {
        mo.getOperations().clear();
        Routing routing = mo.getRouting();
        if (routing == null) return;
        routing.getOperations().stream().sorted(java.util.Comparator.comparingInt(RoutingOperation::getSequence)).forEach(op -> {
            ManufacturingOrderOperation snap = new ManufacturingOrderOperation();
            snap.setManufacturingOrder(mo);
            snap.setSequence(op.getSequence());
            snap.setName(op.getName());
            if (op.getWorkstation() != null) {
                snap.setWorkstationId(op.getWorkstation().getId());
                snap.setWorkstationName(op.getWorkstation().getName());
                snap.setMachineRate(op.getWorkstation().getHourlyRate() != null ? op.getWorkstation().getHourlyRate() : BigDecimal.ZERO);
            }
            snap.setMinutesPerUnit(op.getEstimatedMinutesPerUnit() != null ? op.getEstimatedMinutesPerUnit() : BigDecimal.ZERO);
            snap.setLabourRate(op.getLabourHourlyRate() != null ? op.getLabourHourlyRate() : BigDecimal.ZERO);
            mo.getOperations().add(snap);
        });
    }

    /** What the order is expected to cost: material at today's cost prices, the steps' time at their rates, and the order's indirect costs. */
    private void estimate(ManufacturingOrder mo) {
        BigDecimal qty = mo.getQuantityPlanned();
        BigDecimal material = BigDecimal.ZERO;
        if (mo.getBom() != null) {
            for (Map.Entry<Product, BigDecimal> e : bomService.requiredComponentQuantities(mo.getBom(), qty).entrySet()) {
                BigDecimal unit = e.getKey().getCostPrice() != null ? e.getKey().getCostPrice() : BigDecimal.ZERO;
                material = material.add(unit.multiply(e.getValue()));
            }
        }
        material = material.setScale(2, RoundingMode.HALF_UP);
        BigDecimal labour = BigDecimal.ZERO;
        BigDecimal machine = BigDecimal.ZERO;
        if (!mo.getOperations().isEmpty()) {
            for (ManufacturingOrderOperation op : mo.getOperations()) {
                BigDecimal minutes = op.getMinutesPerUnit().multiply(qty);
                labour = labour.add(charge(minutes, op.getLabourRate()));
                machine = machine.add(charge(minutes, op.getMachineRate()));
            }
        } else if (mo.getBom() != null && mo.getBom().getRouting() != null) {
            for (RoutingOperation op : mo.getBom().getRouting().getOperations()) {
                BigDecimal minutes = (op.getEstimatedMinutesPerUnit() != null ? op.getEstimatedMinutesPerUnit() : BigDecimal.ZERO).multiply(qty);
                labour = labour.add(charge(minutes, op.getLabourHourlyRate()));
                machine = machine.add(charge(minutes, op.getWorkstation() != null ? op.getWorkstation().getHourlyRate() : null));
            }
        }
        BigDecimal overhead = BigDecimal.ZERO;
        for (ManufacturingOrderOverhead oh : mo.getOverheads()) {
            overhead = overhead.add(allocateOverhead(oh, qty, qty, material));
        }
        mo.setPlannedMaterialCost(material);
        mo.setPlannedLabourCost(labour.setScale(2, RoundingMode.HALF_UP));
        mo.setPlannedMachineCost(machine.setScale(2, RoundingMode.HALF_UP));
        mo.setPlannedOverheadCost(overhead.setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public ManufacturingOrderDto getById(Long id) {
        return ManufacturingOrderDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public ManufacturingOrderDto create(CreateManufacturingOrderRequest request) {
        Bom bom = bomService.getEntityOrThrow(request.getBomId());
        ManufacturingOrder mo = new ManufacturingOrder();
        mo.setOrderNumber(String.format("MO-%04d", repository.nextNumber()));
        mo.setProduct(bom.getProduct());
        mo.setBom(bom);
        mo.setQuantityPlanned(request.getQuantityPlanned());
        mo.setScheduledDate(request.getScheduledDate());
        mo.setNote(request.getNote());
        mo.setSourcePlanLineId(request.getSourcePlanLineId());
        if (request.getLocationId() != null) {
            WarehouseLocation location = warehouseService.getUsableLocationOrThrow(request.getLocationId());
            mo.setLocation(location);
            mo.setLocationName(location.getName());
            mo.setWarehouseName(location.getWarehouse().getName());
        }
        attachOverheads(mo, request.getOverheadCostIds());
        estimate(mo);
        ManufacturingOrder saved = repository.save(mo);
        auditLogService.record("MANUFACTURING_ORDER_CREATED",
                "Created manufacturing order " + saved.getOrderNumber() + " for " + Qty.str(saved.getQuantityPlanned()) + " x " + saved.getProduct().getName(),
                "ManufacturingOrder", saved.getId());
        return ManufacturingOrderDto.from(saved);
    }

    private void attachOverheads(ManufacturingOrder mo, List<Long> overheadCostIds) {
        if (overheadCostIds == null) return;
        for (Long overheadCostId : new java.util.LinkedHashSet<>(overheadCostIds)) {
            OverheadCost overheadCost = overheadCostService.getEntityOrThrow(overheadCostId);
            ManufacturingOrderOverhead line = new ManufacturingOrderOverhead();
            line.setManufacturingOrder(mo);
            line.setOverheadCost(overheadCost);
            line.setNameSnapshot(overheadCost.getName());
            line.setMethodSnapshot(overheadCost.getAllocationMethod());
            line.setRateSnapshot(overheadCost.getRate());
            mo.getOverheads().add(line);
        }
    }

    /**
     * MF-3: change an order. A draft can change anything; once confirmed only the date, the note and the planned
     * quantity can (the quantity never below what is already produced; setting it to exactly what is produced closes the order).
     */
    @Transactional
    public ManufacturingOrderDto update(Long id, UpdateManufacturingOrderRequest request) {
        ManufacturingOrder mo = getEntityOrThrow(id);
        ManufacturingOrderStatus status = mo.getStatus();
        if (status == ManufacturingOrderStatus.PRODUCED || status == ManufacturingOrderStatus.CANCELLED) {
            throw ApiException.badRequest("Manufacturing order " + mo.getOrderNumber() + " is " + status + " and can no longer be changed");
        }
        boolean draft = status == ManufacturingOrderStatus.DRAFT;
        if (!draft) {
            if (request.getBomId() != null && !request.getBomId().equals(mo.getBom() != null ? mo.getBom().getId() : null)) {
                throw ApiException.badRequest("The recipe can only be changed while the order is a draft");
            }
            if (request.getOverheadCostIds() != null) {
                throw ApiException.badRequest("The indirect costs can only be changed while the order is a draft");
            }
            if (request.getLocationId() != null && !request.getLocationId().equals(mo.getLocation() != null ? mo.getLocation().getId() : null)) {
                throw ApiException.badRequest("The location can only be changed while the order is a draft");
            }
        }
        if (draft && request.getBomId() != null) {
            mo.setBom(bomService.getEntityOrThrow(request.getBomId()));
            mo.setProduct(mo.getBom().getProduct());
        }
        if (request.getQuantityPlanned() != null) {
            BigDecimal q = request.getQuantityPlanned();
            if (q.compareTo(mo.getQuantityProduced()) < 0) {
                throw ApiException.badRequest("Already produced " + Qty.str(mo.getQuantityProduced()) + " of '" + mo.getProduct().getName()
                        + "' — the planned quantity cannot go below that");
            }
            mo.setQuantityPlanned(q);
        }
        mo.setScheduledDate(request.getScheduledDate());
        mo.setNote(request.getNote() != null && !request.getNote().isBlank() ? request.getNote().trim() : null);
        if (draft && request.getLocationId() != null) {
            WarehouseLocation location = warehouseService.getUsableLocationOrThrow(request.getLocationId());
            mo.setLocation(location);
            mo.setLocationName(location.getName());
            mo.setWarehouseName(location.getWarehouse().getName());
        }
        if (draft && request.getOverheadCostIds() != null) {
            mo.getOverheads().clear();
            attachOverheads(mo, request.getOverheadCostIds());
        }
        if (!draft && mo.getQuantityPlanned().compareTo(mo.getQuantityProduced()) == 0 && mo.getQuantityProduced().signum() > 0) {
            mo.setStatus(ManufacturingOrderStatus.PRODUCED);
            mo.setCompletedAt(java.time.Instant.now());
        }
        estimate(mo);
        ManufacturingOrder saved = repository.save(mo);
        auditLogService.record("MANUFACTURING_ORDER_UPDATED",
                "Changed manufacturing order " + saved.getOrderNumber() + " (" + Qty.str(saved.getQuantityPlanned()) + " x " + saved.getProduct().getName()
                        + (saved.getScheduledDate() != null ? ", scheduled " + saved.getScheduledDate() : "") + ")",
                "ManufacturingOrder", saved.getId());
        return ManufacturingOrderDto.from(saved);
    }

    /** MF-3: a new draft with the same recipe, quantity, indirect costs, location and note — for the order you make again next week. */
    @Transactional
    public ManufacturingOrderDto copy(Long id) {
        ManufacturingOrder source = getEntityOrThrow(id);
        CreateManufacturingOrderRequest req = new CreateManufacturingOrderRequest();
        req.setBomId(source.getBom().getId());
        req.setQuantityPlanned(source.getQuantityPlanned());
        req.setNote(source.getNote());
        req.setLocationId(source.getLocation() != null ? source.getLocation().getId() : null);
        req.setOverheadCostIds(source.getOverheads().stream()
                .map(o -> o.getOverheadCost() != null ? o.getOverheadCost().getId() : null)
                .filter(java.util.Objects::nonNull).toList());
        return create(req);
    }

    /** MF-3: what is short for the quantity still to produce, so the shortage is known before the run is attempted. */
    @Transactional(readOnly = true)
    public ComponentAvailabilityDto availability(Long id) {
        ManufacturingOrder mo = getEntityOrThrow(id);
        boolean open = mo.getStatus() != ManufacturingOrderStatus.PRODUCED && mo.getStatus() != ManufacturingOrderStatus.CANCELLED;
        BigDecimal remaining = open ? mo.getQuantityPlanned().subtract(mo.getQuantityProduced()) : BigDecimal.ZERO;
        WarehouseLocation location = mo.getLocation();
        boolean allowNegative = storeSettingsService.getSettings().allowNegativeStock();
        List<ComponentAvailabilityDto.Line> lines = new java.util.ArrayList<>();
        BigDecimal maxProducible = remaining;
        boolean all = true;
        if (mo.getBom() != null) {
            for (BomLine line : mo.getBom().getLines()) {
                Product component = line.getComponent();
                BigDecimal perUnit = line.getQuantityPerUnit();
                BigDecimal required = perUnit.multiply(remaining);
                BigDecimal available = component.isTrackStock() ? warehouseService.stockAt(component, location) : null;
                BigDecimal shortBy = available != null ? Qty.max(BigDecimal.ZERO, required.subtract(available)) : BigDecimal.ZERO;
                if (shortBy.signum() > 0) all = false;
                if (available != null && perUnit.signum() > 0) maxProducible = Qty.min(maxProducible, Qty.max(BigDecimal.ZERO, available).divide(perUnit, 4, java.math.RoundingMode.DOWN));
                lines.add(new ComponentAvailabilityDto.Line(component.getId(), component.getName(), component.isTrackStock(), perUnit, required, available, shortBy));
            }
        }
        return new ComponentAvailabilityDto(mo.getId(), mo.getOrderNumber(), remaining, all, maxProducible, allowNegative, lines);
    }

    /** DRAFT -> CONFIRMED: locks the order to further edits and snapshots the Bom's routing. */
    @Transactional
    public ManufacturingOrderDto confirm(Long id) {
        ManufacturingOrder mo = getEntityOrThrow(id);
        if (mo.getStatus() != ManufacturingOrderStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft manufacturing order can be confirmed — " + mo.getOrderNumber() + " is " + mo.getStatus());
        }
        mo.setRouting(mo.getBom().getRouting());
        snapshotOperations(mo);
        estimate(mo);
        mo.setStatus(ManufacturingOrderStatus.CONFIRMED);
        ManufacturingOrder saved = repository.save(mo);
        auditLogService.record("MANUFACTURING_ORDER_CONFIRMED", "Confirmed manufacturing order " + saved.getOrderNumber(), "ManufacturingOrder", saved.getId());
        return ManufacturingOrderDto.from(saved);
    }

    /** Allowed any time before anything has actually been produced — once a run exists, cancelling would leave real stock movements unexplained, same "no undoing a real-world event" stance PurchaseOrderService.cancel already takes. */
    @Transactional
    public ManufacturingOrderDto cancel(Long id) {
        ManufacturingOrder mo = getEntityOrThrow(id);
        if (mo.getStatus() != ManufacturingOrderStatus.DRAFT && mo.getStatus() != ManufacturingOrderStatus.CONFIRMED) {
            throw ApiException.badRequest("Cannot cancel manufacturing order " + mo.getOrderNumber() + " — it is " + mo.getStatus());
        }
        mo.setStatus(ManufacturingOrderStatus.CANCELLED);
        ManufacturingOrder saved = repository.save(mo);
        auditLogService.record("MANUFACTURING_ORDER_CANCELLED", "Cancelled manufacturing order " + saved.getOrderNumber(), "ManufacturingOrder", saved.getId());
        return ManufacturingOrderDto.from(saved);
    }

    /**
     * One production run: consumes this run's share of components (skipping any component with
     * {@code trackStock} off — see class javadoc), adds this run's quantity to the finished good's
     * stock (same skip if the finished good itself isn't tracked), and records one {@link
     * ManufacturingOrderRun} with that run's own material/overhead cost. Accepts a partial
     * quantity and can be called more than once against the same order.
     */
    @Transactional
    public ManufacturingOrderDto produce(Long id, ProduceManufacturingOrderRequest request) {
        ManufacturingOrder mo = getEntityOrThrow(id);
        if (mo.getStatus() != ManufacturingOrderStatus.CONFIRMED && mo.getStatus() != ManufacturingOrderStatus.PARTIALLY_PRODUCED) {
            throw ApiException.badRequest("Cannot produce against manufacturing order " + mo.getOrderNumber() + " — it is " + mo.getStatus());
        }
        // Multi-warehouse phase 4: null = legacy primary stock. Re-checked now since the location
        // may have been deactivated since the order was drafted.
        WarehouseLocation location = mo.getLocation();
        if (location != null) {
            warehouseService.assertUsable(location);
        }
        BigDecimal quantityThisRun = request.getQuantity();
        BigDecimal remaining = mo.getQuantityPlanned().subtract(mo.getQuantityProduced());
        if (quantityThisRun.compareTo(remaining) > 0) {
            throw ApiException.badRequest("Cannot produce " + Qty.str(quantityThisRun) + " of '" + mo.getProduct().getName()
                    + "' — only " + Qty.str(remaining) + " remaining (of " + Qty.str(mo.getQuantityPlanned()) + " planned, " + Qty.str(mo.getQuantityProduced()) + " already produced)");
        }

        // MF-4: units started = good + scrapped. Components and machine/labour time are used for all of them.
        BigDecimal quantityScrapped = request.getQuantityScrapped() != null ? request.getQuantityScrapped() : BigDecimal.ZERO;
        if (quantityScrapped.signum() < 0) throw ApiException.badRequest("Scrapped quantity cannot be negative");
        BigDecimal quantityStarted = quantityThisRun.add(quantityScrapped);
        Map<Product, BigDecimal> planned = bomService.requiredComponentQuantities(mo.getBom(), quantityStarted);
        Map<Product, BigDecimal> required = new LinkedHashMap<>(planned);
        if (request.getComponentQuantities() != null) {
            for (ProduceManufacturingOrderRequest.ComponentQuantity cq : request.getComponentQuantities()) {
                if (cq.getComponentId() == null || cq.getQuantity() == null) continue;
                Product match = required.keySet().stream().filter(p -> p.getId().equals(cq.getComponentId())).findFirst()
                        .orElseThrow(() -> ApiException.badRequest("This recipe has no component with id " + cq.getComponentId()));
                if (cq.getQuantity().signum() < 0 || cq.getQuantity().compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
                    throw ApiException.badRequest("The quantity used of '" + match.getName() + "' must be between 0 and 1,000,000");
                }
                required.put(match, cq.getQuantity());
            }
        }
        boolean allowNegativeStock = storeSettingsService.getSettings().allowNegativeStock();
        if (!allowNegativeStock) {
            for (Map.Entry<Product, BigDecimal> entry : required.entrySet()) {
                Product component = entry.getKey();
                if (!component.isTrackStock()) {
                    continue;
                }
                BigDecimal available = warehouseService.stockAt(component, location);
                BigDecimal requested = entry.getValue();
                if (requested.compareTo(available) > 0) {
                    throw ApiException.badRequest("Not enough stock for component '" + component.getName() + "' — " + available
                            + " available, " + Qty.str(requested) + " requested. Enable \"Allow negative stock\" in Inventory settings to produce anyway.");
                }
            }
        }

        ManufacturingOrderRun run = new ManufacturingOrderRun();
        run.setManufacturingOrder(mo);
        run.setQuantityProduced(quantityThisRun);
        run.setQuantityScrapped(quantityScrapped);
        run.setScrapReason(request.getScrapReason() != null && !request.getScrapReason().isBlank() ? request.getScrapReason().trim() : null);

        BigDecimal materialCost = BigDecimal.ZERO;
        Map<Product, BigDecimal> orderedRequired = new LinkedHashMap<>(required);
        for (Map.Entry<Product, BigDecimal> entry : orderedRequired.entrySet()) {
            Product component = entry.getKey();
            BigDecimal quantityConsumed = entry.getValue();
            BigDecimal unitCost = component.getCostPrice() != null ? component.getCostPrice() : BigDecimal.ZERO;
            materialCost = materialCost.add(unitCost.multiply(quantityConsumed));

            ManufacturingOrderConsumption consumption = new ManufacturingOrderConsumption();
            consumption.setRun(run);
            consumption.setComponent(component);
            consumption.setComponentName(component.getName());
            consumption.setQuantityConsumed(quantityConsumed);
            consumption.setPlannedQuantity(planned.get(component));
            consumption.setUnitCost(unitCost);
            // Fourier: "Add track stock attribute on Product" — a non-tracked component is never
            // checked or adjusted, mirroring OrderService/SalesOrderService's own treatment.
            if (component.isTrackStock()) {
                BigDecimal stockBefore = warehouseService.stockAt(component, location);
                catalogService.adjustStock(component, quantityConsumed.negate(), location);
                consumption.setStockBefore(stockBefore);
                consumption.setStockAfter(warehouseService.stockAt(component, location));
            }
            run.getConsumptions().add(consumption);
        }
        materialCost = materialCost.setScale(2, RoundingMode.HALF_UP);
        run.setMaterialCost(materialCost);

        BigDecimal overheadCost = BigDecimal.ZERO;
        for (ManufacturingOrderOverhead overhead : mo.getOverheads()) {
            overheadCost = overheadCost.add(allocateOverhead(overhead, quantityThisRun, mo.getQuantityPlanned(), materialCost));
        }
        overheadCost = overheadCost.setScale(2, RoundingMode.HALF_UP);
        run.setOverheadCost(overheadCost);

        // MF-2: time on the routing's steps. An order confirmed before this existed has no copy of its steps yet: take it now.
        if (mo.getOperations().isEmpty() && mo.getRouting() != null) {
            snapshotOperations(mo);
        }
        Map<Integer, BigDecimal> actual = new java.util.HashMap<>();
        if (request.getOperationMinutes() != null) {
            for (ProduceManufacturingOrderRequest.OperationMinutes om : request.getOperationMinutes()) {
                if (om.getSequence() == null || om.getMinutes() == null) continue;
                if (om.getMinutes().signum() < 0 || om.getMinutes().compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
                    throw ApiException.badRequest("The minutes for step " + om.getSequence() + " must be between 0 and 1,000,000");
                }
                boolean known = mo.getOperations().stream().anyMatch(o -> o.getSequence() == om.getSequence());
                if (!known) throw ApiException.badRequest("This order has no step number " + om.getSequence());
                actual.put(om.getSequence(), om.getMinutes());
            }
        }
        BigDecimal labourCost = BigDecimal.ZERO;
        BigDecimal machineCost = BigDecimal.ZERO;
        for (ManufacturingOrderOperation op : mo.getOperations()) {
            BigDecimal plannedMinutes = op.getMinutesPerUnit().multiply(quantityStarted).setScale(2, RoundingMode.HALF_UP);
            BigDecimal minutes = actual.containsKey(op.getSequence()) ? actual.get(op.getSequence()).setScale(2, RoundingMode.HALF_UP) : plannedMinutes;
            ManufacturingOrderRunOperation ro = new ManufacturingOrderRunOperation();
            ro.setRun(run);
            ro.setSequence(op.getSequence());
            ro.setName(op.getName());
            ro.setWorkstationId(op.getWorkstationId());
            ro.setWorkstationName(op.getWorkstationName());
            ro.setPlannedMinutes(plannedMinutes);
            ro.setActualMinutes(minutes);
            ro.setMachineCost(charge(minutes, op.getMachineRate()));
            ro.setLabourCost(charge(minutes, op.getLabourRate()));
            labourCost = labourCost.add(ro.getLabourCost());
            machineCost = machineCost.add(ro.getMachineCost());
            run.getOperations().add(ro);
        }
        run.setLabourCost(labourCost.setScale(2, RoundingMode.HALF_UP));
        run.setMachineCost(machineCost.setScale(2, RoundingMode.HALF_UP));
        run.setTotalCost(materialCost.add(overheadCost).add(run.getLabourCost()).add(run.getMachineCost()).setScale(2, RoundingMode.HALF_UP));

        // MF-4: the scrapped units' share of the run, and what by-products take off the good units' cost.
        BigDecimal runTotal = run.getTotalCost();
        run.setScrapCost(quantityScrapped.signum() > 0
                ? runTotal.multiply(quantityScrapped).divide(quantityStarted, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        BigDecimal byProductCredit = BigDecimal.ZERO;
        for (BomByProduct bp : mo.getBom().getByProducts()) {
            BigDecimal qty = bp.getQuantityPerUnit().multiply(quantityThisRun);
            ManufacturingOrderRunByProduct rb = new ManufacturingOrderRunByProduct();
            rb.setRun(run);
            rb.setProduct(bp.getProduct());
            rb.setProductName(bp.getProduct().getName());
            rb.setQuantity(qty);
            BigDecimal share = bp.getCostSharePercent() != null ? bp.getCostSharePercent() : BigDecimal.ZERO;
            rb.setCostValue(runTotal.multiply(share).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            byProductCredit = byProductCredit.add(rb.getCostValue());
            if (bp.getProduct().isTrackStock()) {
                rb.setStockBefore(warehouseService.stockAt(bp.getProduct(), location));
                catalogService.adjustStock(bp.getProduct(), qty, location);
                rb.setStockAfter(warehouseService.stockAt(bp.getProduct(), location));
            }
            run.getByProducts().add(rb);
        }
        run.setByProductCredit(byProductCredit);

        Product product = mo.getProduct();
        if (product.isTrackStock()) {
            BigDecimal stockBefore = warehouseService.stockAt(product, location);
            catalogService.adjustStock(product, quantityThisRun, location);
            run.setStockBefore(stockBefore);
            run.setStockAfter(warehouseService.stockAt(product, location));
        }
        mo.getRuns().add(run);

        mo.setQuantityProduced(mo.getQuantityProduced().add(quantityThisRun));
        boolean fullyProduced = mo.getQuantityProduced().compareTo(mo.getQuantityPlanned()) >= 0;
        mo.setStatus(fullyProduced ? ManufacturingOrderStatus.PRODUCED : ManufacturingOrderStatus.PARTIALLY_PRODUCED);
        if (fullyProduced) {
            mo.setCompletedAt(java.time.Instant.now());
        }

        ManufacturingOrder saved = repository.save(mo);
        auditLogService.record("MANUFACTURING_ORDER_PRODUCED",
                "Produced " + Qty.str(quantityThisRun) + " x " + saved.getProduct().getName() + " against manufacturing order " + saved.getOrderNumber() + " (now " + saved.getStatus() + ")",
                "ManufacturingOrder", saved.getId());

        // Fourier: "all...Manufacturing module will be integrated when accounting is enabled" —
        // see AccountingService.postManufacturingRun's own javadoc. The saved order's last run is the persisted copy of `run` (it carries the generated id the journal entry points back at).
        ManufacturingOrderRun persistedRun = saved.getRuns().isEmpty() ? run : saved.getRuns().get(saved.getRuns().size() - 1);
        accountingService.postManufacturingRun(saved, persistedRun);

        return ManufacturingOrderDto.from(saved);
    }

    /**
     * Applies one overhead pool's allocation rule for this run — see {@link
     * OverheadAllocationMethod}'s javadoc for what each method means. FLAT is spread by this run's
     * share of the order's total planned quantity so a fully-produced order's runs sum to exactly
     * the flat rate, never more.
     */
    private BigDecimal allocateOverhead(ManufacturingOrderOverhead overhead, BigDecimal quantityThisRun, BigDecimal quantityPlanned, BigDecimal materialCostThisRun) {
        BigDecimal rate = overhead.getRateSnapshot() != null ? overhead.getRateSnapshot() : BigDecimal.ZERO;
        return switch (overhead.getMethodSnapshot()) {
            case PER_UNIT -> rate.multiply(quantityThisRun);
            case PERCENT_OF_MATERIAL_COST -> materialCostThisRun.multiply(rate).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            case FLAT -> quantityPlanned.signum() > 0
                    ? rate.multiply(quantityThisRun).divide(quantityPlanned, 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        };
    }

    /** Used by ProductionPlanService to resolve an order reference without exposing the repository outside this package. */
    @Transactional(readOnly = true)
    public ManufacturingOrder getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Manufacturing order not found: " + id));
    }
}
