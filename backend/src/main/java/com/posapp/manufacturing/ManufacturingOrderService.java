package com.posapp.manufacturing;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.manufacturing.dto.CreateManufacturingOrderRequest;
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

    @Transactional(readOnly = true)
    public ManufacturingOrderDto getById(Long id) {
        return ManufacturingOrderDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public ManufacturingOrderDto create(CreateManufacturingOrderRequest request) {
        Bom bom = bomService.getEntityOrThrow(request.getBomId());
        ManufacturingOrder mo = new ManufacturingOrder();
        mo.setOrderNumber("MO-" + System.currentTimeMillis());
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
        if (request.getOverheadCostIds() != null) {
            for (Long overheadCostId : request.getOverheadCostIds()) {
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
        ManufacturingOrder saved = repository.save(mo);
        auditLogService.record("MANUFACTURING_ORDER_CREATED",
                "Created manufacturing order " + saved.getOrderNumber() + " for " + saved.getQuantityPlanned() + " x " + saved.getProduct().getName(),
                "ManufacturingOrder", saved.getId());
        return ManufacturingOrderDto.from(saved);
    }

    /** DRAFT -> CONFIRMED: locks the order to further edits and snapshots the Bom's routing. */
    @Transactional
    public ManufacturingOrderDto confirm(Long id) {
        ManufacturingOrder mo = getEntityOrThrow(id);
        if (mo.getStatus() != ManufacturingOrderStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft manufacturing order can be confirmed — " + mo.getOrderNumber() + " is " + mo.getStatus());
        }
        mo.setRouting(mo.getBom().getRouting());
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
        int quantityThisRun = request.getQuantity();
        int remaining = mo.getQuantityPlanned() - mo.getQuantityProduced();
        if (quantityThisRun > remaining) {
            throw ApiException.badRequest("Cannot produce " + quantityThisRun + " of '" + mo.getProduct().getName()
                    + "' — only " + remaining + " remaining (of " + mo.getQuantityPlanned() + " planned, " + mo.getQuantityProduced() + " already produced)");
        }

        Map<Product, Integer> required = bomService.requiredComponentQuantities(mo.getBom(), quantityThisRun);
        boolean allowNegativeStock = storeSettingsService.getSettings().allowNegativeStock();
        if (!allowNegativeStock) {
            for (Map.Entry<Product, Integer> entry : required.entrySet()) {
                Product component = entry.getKey();
                if (!component.isTrackStock()) {
                    continue;
                }
                int available = warehouseService.stockAt(component, location);
                int requested = entry.getValue();
                if (requested > available) {
                    throw ApiException.badRequest("Not enough stock for component '" + component.getName() + "' — " + available
                            + " available, " + requested + " requested. Enable \"Allow negative stock\" in Inventory settings to produce anyway.");
                }
            }
        }

        ManufacturingOrderRun run = new ManufacturingOrderRun();
        run.setManufacturingOrder(mo);
        run.setQuantityProduced(quantityThisRun);

        BigDecimal materialCost = BigDecimal.ZERO;
        Map<Product, Integer> orderedRequired = new LinkedHashMap<>(required);
        for (Map.Entry<Product, Integer> entry : orderedRequired.entrySet()) {
            Product component = entry.getKey();
            int quantityConsumed = entry.getValue();
            BigDecimal unitCost = component.getCostPrice() != null ? component.getCostPrice() : BigDecimal.ZERO;
            materialCost = materialCost.add(unitCost.multiply(BigDecimal.valueOf(quantityConsumed)));

            ManufacturingOrderConsumption consumption = new ManufacturingOrderConsumption();
            consumption.setRun(run);
            consumption.setComponent(component);
            consumption.setComponentName(component.getName());
            consumption.setQuantityConsumed(quantityConsumed);
            consumption.setUnitCost(unitCost);
            // Fourier: "Add track stock attribute on Product" — a non-tracked component is never
            // checked or adjusted, mirroring OrderService/SalesOrderService's own treatment.
            if (component.isTrackStock()) {
                int stockBefore = warehouseService.stockAt(component, location);
                catalogService.adjustStock(component, -quantityConsumed, location);
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
        run.setTotalCost(materialCost.add(overheadCost).setScale(2, RoundingMode.HALF_UP));

        Product product = mo.getProduct();
        if (product.isTrackStock()) {
            int stockBefore = warehouseService.stockAt(product, location);
            catalogService.adjustStock(product, quantityThisRun, location);
            run.setStockBefore(stockBefore);
            run.setStockAfter(warehouseService.stockAt(product, location));
        }
        mo.getRuns().add(run);

        mo.setQuantityProduced(mo.getQuantityProduced() + quantityThisRun);
        boolean fullyProduced = mo.getQuantityProduced() >= mo.getQuantityPlanned();
        mo.setStatus(fullyProduced ? ManufacturingOrderStatus.PRODUCED : ManufacturingOrderStatus.PARTIALLY_PRODUCED);
        if (fullyProduced) {
            mo.setCompletedAt(java.time.Instant.now());
        }

        ManufacturingOrder saved = repository.save(mo);
        auditLogService.record("MANUFACTURING_ORDER_PRODUCED",
                "Produced " + quantityThisRun + " x " + saved.getProduct().getName() + " against manufacturing order " + saved.getOrderNumber() + " (now " + saved.getStatus() + ")",
                "ManufacturingOrder", saved.getId());

        // Fourier: "all...Manufacturing module will be integrated when accounting is enabled" —
        // `run` is the same managed instance cascaded into the save above, so it already carries
        // its generated id here; see AccountingService.postManufacturingRun's own javadoc.
        accountingService.postManufacturingRun(saved, run);

        return ManufacturingOrderDto.from(saved);
    }

    /**
     * Applies one overhead pool's allocation rule for this run — see {@link
     * OverheadAllocationMethod}'s javadoc for what each method means. FLAT is spread by this run's
     * share of the order's total planned quantity so a fully-produced order's runs sum to exactly
     * the flat rate, never more.
     */
    private BigDecimal allocateOverhead(ManufacturingOrderOverhead overhead, int quantityThisRun, int quantityPlanned, BigDecimal materialCostThisRun) {
        BigDecimal rate = overhead.getRateSnapshot() != null ? overhead.getRateSnapshot() : BigDecimal.ZERO;
        return switch (overhead.getMethodSnapshot()) {
            case PER_UNIT -> rate.multiply(BigDecimal.valueOf(quantityThisRun));
            case PERCENT_OF_MATERIAL_COST -> materialCostThisRun.multiply(rate).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            case FLAT -> quantityPlanned > 0
                    ? rate.multiply(BigDecimal.valueOf(quantityThisRun)).divide(BigDecimal.valueOf(quantityPlanned), 6, RoundingMode.HALF_UP)
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
