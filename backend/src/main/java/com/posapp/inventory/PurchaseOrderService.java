package com.posapp.inventory;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.bill.Bill;
import com.posapp.bill.BillRepository;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.common.GlobalDiscountType;
import com.posapp.common.TaxMath;
import com.posapp.tax.TaxRate;
import com.posapp.inventory.dto.PurchaseOrderDto;
import com.posapp.inventory.dto.PurchaseOrderRequest;
import com.posapp.inventory.dto.ReceivePurchaseOrderRequest;
import com.posapp.settings.StoreSettingsService;
import com.posapp.branch.BranchService;
import com.posapp.warehouse.Warehouse;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Task: "Inventory depth ... purchase orders ... a stock-receiving flow that increments quantity
 * with a paper trail." A PO moves DRAFT -> SENT -> (PARTIALLY_RECEIVED ->)* RECEIVED, or DRAFT/SENT
 * -> CANCELLED — see {@link PurchaseOrderStatus}'s javadoc. Only {@link #receiveLines} ever moves
 * real stock, via {@link CatalogService#adjustStock(Product, int, WarehouseLocation)}; creating,
 * editing or sending a PO never does, since ordering something isn't the same as having it in
 * hand — that's exactly the gap Fourier's "Inventory depth" note called out ("today stock is a
 * single quantity field you edit directly"). {@link #receiveLines} also writes one {@link
 * StockReceipt} per product received (Task 1: "Inventory Report") — see that entity's javadoc for
 * why it exists alongside the line's own cumulative {@code quantityReceived}.
 *
 * <p>Fourier: "What is we give location type of RECEIVING,SALES,STORAGE,DAMAGED" — a received
 * line now lands on a warehouse's RECEIVING location (not {@code Product.stockQuantity}
 * directly — see {@link #receiveLines}), so it no longer shows as sellable until someone runs a
 * Stock Transfer to a Sales location. Multi-warehouse phase 2: which warehouse is the order's own
 * optional {@code receivingWarehouse} (null = the primary warehouse, i.e. the hub or a
 * single-warehouse shop); the other non-POS stock-moving modules (Manufacturing, Packaging,
 * Restaurant) still always use the primary warehouse.
 */
@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierService supplierService;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final StockReceiptRepository stockReceiptRepository;
    private final SupplierPaymentService supplierPaymentService;
    private final AccountingService accountingService;
    /** Fourier: "same workflow as Sale order" — read directly (never through {@code BillService}, which itself depends on this class) purely to show "View bill"/"Generate bill" and fold a bill's own paid/outstanding figures into this order's own DTO; same "read the domain's repository directly" convention {@code PurchaseReturnService} already follows for {@code PurchaseOrderRepository}. */
    private final BillRepository billRepository;
    private final StoreSettingsService storeSettingsService;
    /** Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — resolves the primary warehouse's Receiving location; see this class's own javadoc. */
    private final WarehouseService warehouseService;
    /** Applies the branch's document prefix to new order numbers. */
    private final BranchService branchService;
    /** Records batch/expiry and serial numbers as stock is received. */
    private final com.posapp.tracking.TrackingService trackingService;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                 SupplierService supplierService,
                                 CatalogService catalogService,
                                 AuditLogService auditLogService,
                                 StockReceiptRepository stockReceiptRepository,
                                 SupplierPaymentService supplierPaymentService,
                                 AccountingService accountingService,
                                 BillRepository billRepository,
                                 StoreSettingsService storeSettingsService,
                                 WarehouseService warehouseService,
                                 BranchService branchService,
                                 com.posapp.tracking.TrackingService trackingService) {
        this.trackingService = trackingService;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierService = supplierService;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
        this.stockReceiptRepository = stockReceiptRepository;
        this.supplierPaymentService = supplierPaymentService;
        this.accountingService = accountingService;
        this.warehouseService = warehouseService;
        this.branchService = branchService;
        this.billRepository = billRepository;
        this.storeSettingsService = storeSettingsService;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderDto> listAll() {
        return purchaseOrderRepository.findAllByOrderByOrderDateDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDto getById(Long id) {
        return toDto(getEntityOrThrow(id));
    }

    /**
     * Task 5: "Add Record payment on purchase order view or list (if not paid)" — every
     * PurchaseOrderDto now carries its own paid/outstanding figures, computed the same way
     * SupplierPaymentService already does for the Purchase Orders report. Task 4: "Supplier
     * Purchase Return" widened this further to also carry the returned/refund-due figures.
     */
    private PurchaseOrderDto toDto(PurchaseOrder po) {
        Bill bill = billRepository.findByPurchaseOrderId(po.getId()).orElse(null);
        Warehouse receivingWarehouse = receivingWarehouseOf(po);
        return PurchaseOrderDto.from(po,
                SupplierPaymentService.totalCost(po), SupplierPaymentService.netCost(po), SupplierPaymentService.taxCost(po),
                SupplierPaymentService.discountCost(po),
                supplierPaymentService.amountPaidForOrder(po),
                supplierPaymentService.amountOwedForOrder(po),
                supplierPaymentService.returnedAmountForOrder(po),
                supplierPaymentService.refundDueFromSupplierForOrder(po),
                bill != null ? bill.getId() : null,
                bill != null ? bill.getBillNumber() : null,
                receivingWarehouse.getId(), receivingWarehouse.getName());
    }

    /**
     * Multi-warehouse phase 2 — the warehouse this order receives into: the one chosen on the
     * order, or the primary warehouse when none was (every order created before the column
     * existed, and any order on a single-warehouse shop).
     */
    private Warehouse receivingWarehouseOf(PurchaseOrder po) {
        return po.getReceivingWarehouse() != null ? po.getReceivingWarehouse() : warehouseService.getDefaultWarehouseOrThrow();
    }

    @Transactional
    public PurchaseOrderDto createDraft(PurchaseOrderRequest request) {
        PurchaseOrder po = new PurchaseOrder();
        po.setSupplier(supplierService.getEntityOrThrow(request.getSupplierId()));
        // Same "POS-" + millis shape as SaleOrder.invoiceNumber/SalesReturn.returnNumber — a
        // simple, always-unique-enough identifier with no separate sequence table.
        applyRequest(po, request);
        // Numbered after applyRequest so the receiving warehouse's branch prefix can be applied.
        po.setOrderNumber(branchService.prefixedNumber(po.getBranchId(), "PO-" + System.currentTimeMillis()));
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        auditLogService.record("PURCHASE_ORDER_CREATED",
                "Created purchase order " + saved.getOrderNumber() + " for " + saved.getSupplier().getName()
                        + " (" + saved.getLines().size() + " line" + (saved.getLines().size() == 1 ? "" : "s") + ")",
                "PurchaseOrder", saved.getId());
        return toDto(saved);
    }

    /** Only a DRAFT can still be edited — see PurchaseOrderStatus's javadoc. */
    @Transactional
    public PurchaseOrderDto updateDraft(Long id, PurchaseOrderRequest request) {
        PurchaseOrder po = getEntityOrThrow(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft purchase order can be edited — " + po.getOrderNumber() + " is " + po.getStatus());
        }
        po.setSupplier(supplierService.getEntityOrThrow(request.getSupplierId()));
        // orphanRemoval on PurchaseOrder.lines means clearing and re-adding replaces the whole
        // line set in one go — a draft has no partial-receipt history yet to preserve, so there's
        // nothing a line-by-line diff would protect that a full replace doesn't already handle.
        po.getLines().clear();
        applyRequest(po, request);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        auditLogService.record("PURCHASE_ORDER_UPDATED", "Updated purchase order " + saved.getOrderNumber(), "PurchaseOrder", saved.getId());
        return toDto(saved);
    }

    /** DRAFT -> SENT: locks the order to further line edits (see PurchaseOrderStatus) and marks it as actually placed with the supplier. */
    @Transactional
    public PurchaseOrderDto send(Long id) {
        PurchaseOrder po = getEntityOrThrow(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft purchase order can be sent — " + po.getOrderNumber() + " is " + po.getStatus());
        }
        po.setStatus(PurchaseOrderStatus.SENT);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        auditLogService.record("PURCHASE_ORDER_SENT", "Sent purchase order " + saved.getOrderNumber() + " to " + saved.getSupplier().getName(), "PurchaseOrder", saved.getId());
        return toDto(saved);
    }

    /** Allowed any time before anything has actually been received — once even one unit has arrived, cancelling would leave that stock bump unexplained, so it's blocked (same "no undoing a real-world event" stance as OrderService.voidOrder takes after a sale is already fiscalized). */
    @Transactional
    public PurchaseOrderDto cancel(Long id) {
        PurchaseOrder po = getEntityOrThrow(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT && po.getStatus() != PurchaseOrderStatus.SENT) {
            throw ApiException.badRequest("Cannot cancel purchase order " + po.getOrderNumber() + " — it is " + po.getStatus());
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        auditLogService.record("PURCHASE_ORDER_CANCELLED", "Cancelled purchase order " + saved.getOrderNumber(), "PurchaseOrder", saved.getId());
        return toDto(saved);
    }

    /**
     * The actual "stock-receiving flow that increments quantity with a paper trail": each entry
     * bumps one line's {@code quantityReceived} and, in the same transaction, increments the
     * matching product's live stock by the same amount via {@link CatalogService#adjustStock} —
     * the PO + this call together *are* the paper trail (who received what, against which order,
     * and when — the "when"/"who" coming from this same action's own AuditLog entry). Accepts a
     * partial delivery: an entry doesn't have to cover a line's full remaining quantity, and this
     * can be called more than once against the same PO as more of it arrives.
     */
    /** Batch/expiry or serial numbers captured with a received line; serial products need one number per unit. */
    private void recordTracking(PurchaseOrder po, PurchaseOrderLine line, ReceivePurchaseOrderRequest.ReceiveLineEntry entry, WarehouseLocation location) {
        Product product = line.getProduct();
        switch (com.posapp.tracking.TrackingService.modeOf(product)) {
            case BATCH -> trackingService.receiveBatch(product, location, entry.getBatchNumber(), entry.getExpiryDate(),
                    entry.getQuantityReceived(), line.getUnitCost(), po.getOrderNumber());
            case SERIAL -> {
                List<String> serials = entry.getSerialNumbers() != null ? trackingService.cleanSerials(product, entry.getSerialNumbers()) : List.of();
                if (serials.size() != entry.getQuantityReceived()) {
                    throw ApiException.badRequest("'" + line.getProductName() + "' is serial-numbered: enter exactly " + entry.getQuantityReceived()
                            + " serial number" + (entry.getQuantityReceived() == 1 ? "" : "s") + " (" + serials.size() + " given)");
                }
                trackingService.receiveSerials(product, location, serials, line.getUnitCost(), po.getOrderNumber());
            }
            default -> { }
        }
    }

    @Transactional
    public PurchaseOrderDto receiveLines(Long id, ReceivePurchaseOrderRequest request) {
        PurchaseOrder po = getEntityOrThrow(id);
        if (po.getStatus() != PurchaseOrderStatus.SENT && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw ApiException.badRequest("Cannot receive against purchase order " + po.getOrderNumber() + " — it is " + po.getStatus());
        }

        // Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — resolved once for the
        // whole call; every line received against this PO lands here, not on Product.stockQuantity.
        Warehouse receivingWarehouse = receivingWarehouseOf(po);
        if (!receivingWarehouse.isActive()) {
            throw ApiException.badRequest("Cannot receive against purchase order " + po.getOrderNumber() + " — its receiving warehouse '"
                    + receivingWarehouse.getName() + "' is inactive. Reactivate it first.");
        }
        WarehouseLocation receivingLocation = warehouseService.getReceivingLocationOrThrow(receivingWarehouse);

        BigDecimal landedCost = request.getLandedCost() != null ? request.getLandedCost() : BigDecimal.ZERO;
        if (landedCost.signum() < 0) {
            throw ApiException.badRequest("Landed cost cannot be negative");
        }
        if (landedCost.signum() > 0 && "MANUAL".equals(costingMethod())) {
            throw ApiException.badRequest("Landed cost is added to product cost automatically, which the Manual costing method turns off. Change the costing method in Inventory settings first.");
        }
        List<CostSlice> costSlices = new ArrayList<>();
        StringBuilder receivedDescription = new StringBuilder();
        // Accounting's Inventory/Input-VAT/AP figures for exactly this call's received quantities
        // — net and tax computed separately (Fourier: "for purchases, sales, invoices, bills the
        // tax are not being captured") so the tax portion can land in Input VAT Recoverable
        // instead of being buried inside Inventory's own cost — see
        // AccountingService.postPurchaseReceipt's own javadoc.
        BigDecimal netValue = BigDecimal.ZERO;
        BigDecimal taxValue = BigDecimal.ZERO;
        for (ReceivePurchaseOrderRequest.ReceiveLineEntry entry : request.getLines()) {
            PurchaseOrderLine line = po.getLines().stream()
                    .filter(l -> l.getId().equals(entry.getLineId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Line " + entry.getLineId() + " does not belong to purchase order " + po.getOrderNumber()));
            int remaining = line.getQuantityOrdered() - line.getQuantityReceived();
            if (entry.getQuantityReceived() > remaining) {
                throw ApiException.badRequest("Cannot receive " + entry.getQuantityReceived() + " of '" + line.getProductName()
                        + "' — only " + remaining + " remaining (of " + line.getQuantityOrdered() + " ordered, " + line.getQuantityReceived() + " already received)");
            }
            line.setQuantityReceived(line.getQuantityReceived() + entry.getQuantityReceived());
            int stockBefore = line.getProduct() != null ? warehouseService.getStockQuantity(line.getProduct(), receivingLocation) : 0;
            // Everything held across all warehouses before this delivery lands — the base for the weighted-average cost below.
            int totalOnHandBefore = line.getProduct() != null ? warehouseService.totalOnHand(line.getProduct()) : 0;
            if (line.getProduct() != null) {
                catalogService.adjustStock(line.getProduct(), entry.getQuantityReceived(), receivingLocation);
                recordTracking(po, line, entry, receivingLocation);
            }
            int stockAfter = line.getProduct() != null ? warehouseService.getStockQuantity(line.getProduct(), receivingLocation) : stockBefore;
            // Task 1: "Inventory Report" (Stock movement history) — one structured, dated row per
            // product actually received, see StockReceipt's own javadoc for why this exists
            // alongside the line's own cumulative quantityReceived and the AuditLog entry below.
            if (entry.getQuantityReceived() != 0) {
                StockReceipt receipt = new StockReceipt();
                receipt.setPurchaseOrder(po);
                receipt.setOrderNumber(po.getOrderNumber());
                receipt.setProduct(line.getProduct());
                receipt.setProductName(line.getProductName());
                receipt.setQuantityReceived(entry.getQuantityReceived());
                receipt.setStockBefore(stockBefore);
                receipt.setStockAfter(stockAfter);
                receipt.setUnitCost(line.getUnitCost());
                stockReceiptRepository.save(receipt);
                if (line.getUnitCost() != null) {
                    boolean inclusive = Boolean.TRUE.equals(line.getTaxInclusive());
                    BigDecimal grossReceivedBeforeDiscount = line.getUnitCost().multiply(BigDecimal.valueOf(entry.getQuantityReceived()));
                    // Fourier: "Except POS other module can have global discount ... Either on
                    // line or global" — the effective discountPercent (PER_LINE or GLOBAL,
                    // whichever applies) knocked off this received slice too, before tax is
                    // computed on it, same ordering as SupplierPaymentService's own lineGross/lineTax.
                    BigDecimal discountPercent = TaxMath.effectiveDiscountPercent(po.getDiscountPercent(), line.getDiscountPercent());
                    BigDecimal grossReceived = TaxMath.applyDiscount(grossReceivedBeforeDiscount, discountPercent);
                    BigDecimal taxReceived = TaxMath.taxOnAmount(grossReceived, line.getTaxRatePercent(), inclusive);
                    BigDecimal netThisLine = TaxMath.netOfTax(grossReceived, taxReceived, inclusive);
                    netValue = netValue.add(netThisLine);
                    taxValue = taxValue.add(taxReceived);
                    // The product's cost follows what it actually cost to buy (per the costing method);
                    // applied after the loop, once the landed cost can be spread over the whole delivery.
                    if (entry.getQuantityReceived() > 0) {
                        costSlices.add(new CostSlice(line.getProduct(), totalOnHandBefore, entry.getQuantityReceived(), netThisLine));
                    }
                }
            }
            if (receivedDescription.length() > 0) receivedDescription.append(", ");
            receivedDescription.append(entry.getQuantityReceived()).append(" x ").append(line.getProductName());
        }

        applyReceiptCosts(po, costSlices, landedCost);
        if (landedCost.signum() > 0) {
            po.setLandedCostTotal((po.getLandedCostTotal() != null ? po.getLandedCostTotal() : BigDecimal.ZERO).add(landedCost));
        }

        boolean allReceived = po.getLines().stream().allMatch(l -> l.getQuantityReceived() >= l.getQuantityOrdered());
        boolean anyReceived = po.getLines().stream().anyMatch(l -> l.getQuantityReceived() > 0);
        po.setStatus(allReceived ? PurchaseOrderStatus.RECEIVED : anyReceived ? PurchaseOrderStatus.PARTIALLY_RECEIVED : po.getStatus());

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        auditLogService.record("PURCHASE_ORDER_RECEIVED",
                "Received " + receivedDescription + " against purchase order " + saved.getOrderNumber() + " (now " + saved.getStatus() + ")",
                "PurchaseOrder", saved.getId());

        // Fourier: "all...Inventory...module will be integrated when accounting is enabled" — see
        // AccountingService.postPurchaseReceipt's own javadoc.
        accountingService.postPurchaseReceipt(saved, netValue.setScale(2, RoundingMode.HALF_UP), taxValue.setScale(2, RoundingMode.HALF_UP));
        if (landedCost.signum() > 0) {
            accountingService.postLandedCost(saved, landedCost.setScale(2, RoundingMode.HALF_UP), request.isLandedCostPaid(), request.getLandedCostNote());
            auditLogService.record("PURCHASE_ORDER_LANDED_COST",
                    "Added landed cost " + landedCost.setScale(2, RoundingMode.HALF_UP) + (request.isLandedCostPaid() ? " (paid)" : " (owed)")
                            + " to the cost of goods received on purchase order " + saved.getOrderNumber(),
                    "PurchaseOrder", saved.getId());
        }

        return toDto(saved);
    }

    /** One delivered line, kept until the whole delivery is known so landed cost can be spread over it. */
    private record CostSlice(Product product, int onHandBefore, int quantity, BigDecimal netValue) {
    }

    private String costingMethod() {
        String method = storeSettingsService.getSettings().costingMethod();
        return method == null || method.isBlank() ? "WEIGHTED_AVERAGE" : method;
    }

    /**
     * Updates each delivered product's cost per the store's costing method. Landed cost is spread over the
     * delivered lines in proportion to their value (by quantity if the delivery has no value) and counts as part
     * of what the goods cost. MANUAL leaves cost untouched.
     */
    private void applyReceiptCosts(PurchaseOrder po, List<CostSlice> slices, BigDecimal landedCost) {
        String method = costingMethod();
        if ("MANUAL".equals(method) || slices.isEmpty()) return;
        BigDecimal totalNet = BigDecimal.ZERO;
        long totalQty = 0;
        for (CostSlice s : slices) {
            totalNet = totalNet.add(s.netValue());
            totalQty += s.quantity();
        }
        for (CostSlice s : slices) {
            Product product = s.product();
            if (product == null || !product.isTrackStock() || product.isService()) continue;
            BigDecimal share = BigDecimal.ZERO;
            if (landedCost.signum() > 0) {
                share = totalNet.signum() > 0
                        ? landedCost.multiply(s.netValue()).divide(totalNet, 4, RoundingMode.HALF_UP)
                        : landedCost.multiply(BigDecimal.valueOf(s.quantity())).divide(BigDecimal.valueOf(totalQty), 4, RoundingMode.HALF_UP);
            }
            BigDecimal value = s.netValue().add(share);
            BigDecimal previousCost = product.getCostPrice();
            BigDecimal newCost = "LAST_PURCHASE".equals(method)
                    ? catalogService.applyLastPurchaseCost(product, s.quantity(), value)
                    : catalogService.applyWeightedAverageCost(product, s.onHandBefore(), s.quantity(), value);
            if (newCost != null) {
                auditLogService.record("PRODUCT_COST_UPDATED",
                        "Cost of " + product.getName() + " changed from " + (previousCost != null ? previousCost : BigDecimal.ZERO) + " to " + newCost
                                + " (" + ("LAST_PURCHASE".equals(method) ? "last purchase" : "weighted average") + " on receiving purchase order " + po.getOrderNumber()
                                + (share.signum() > 0 ? ", incl. landed cost " + share.setScale(2, RoundingMode.HALF_UP) : "") + ")",
                        "Product", product.getId());
            }
        }
    }

    private PurchaseOrder getEntityOrThrow(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Purchase order not found: " + id));
    }

    private void applyRequest(PurchaseOrder po, PurchaseOrderRequest request) {
        // Multi-warehouse phase 2 — optional; omitted means "primary warehouse" (stored as null).
        // PurchaseRequisitionService builds a request without it, which lands here as null too.
        if (request.getReceivingWarehouseId() != null) {
            Warehouse chosen = warehouseService.getEntityOrThrow(request.getReceivingWarehouseId());
            if (!chosen.isActive()) {
                throw ApiException.badRequest("Warehouse '" + chosen.getName() + "' is inactive and cannot receive stock");
            }
            po.setReceivingWarehouse(chosen);
        } else {
            po.setReceivingWarehouse(null);
        }
        po.setBranchId(warehouseService.branchIdOf(po.getReceivingWarehouse()));
        po.setExpectedDate(request.getExpectedDate());
        po.setNotes(request.getNotes());
        List<PurchaseOrderLine> lines = request.getLines().stream().map(entry -> {
            Product product = catalogService.getProductOrThrow(entry.getProductId());
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setPurchaseOrder(po);
            line.setProduct(product);
            line.setProductName(product.getName());
            line.setQuantityOrdered(entry.getQuantityOrdered());
            line.setUnitCost(entry.getUnitCost() != null ? entry.getUnitCost() : product.getCostPrice());
            // Fourier: "Same for discount (but discount should be activate in the setting or
            // configuration) and it will be per line" — validated/capped (including whether the
            // feature is even turned on) before ever reaching the line.
            line.setDiscountPercent(storeSettingsService.validateLineDiscount(entry.getDiscountPercent()));
            // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
            // snapshot the product's currently-assigned tax rate onto the line, same as
            // OrderService.buildLine does for a POS cart line.
            TaxRate taxRate = product.getTaxRate();
            if (taxRate != null) {
                line.setTaxCode(taxRate.getCode());
                line.setTaxRatePercent(taxRate.getRatePercent());
                line.setTaxInclusive(taxRate.isInclusive());
            }
            return line;
        }).collect(Collectors.toList());
        // Fourier: "Except POS other module can have global discount ... Either on line or
        // global", then "Global discount should be a fixed amount or percentage with
        // percentage as the default" — resolved after the lines above are built, since a
        // FIXED_AMOUNT entry needs each line's own raw (pre-discount) gross to convert into
        // the equivalent percentage; validated/capped (including whether the store's discount
        // mode is actually GLOBAL) the same as a directly-entered percentage always was.
        GlobalDiscountType discountType = GlobalDiscountType.parse(request.getDiscountType());
        BigDecimal requestedPercent;
        if (discountType == GlobalDiscountType.FIXED_AMOUNT) {
            BigDecimal rawSubtotal = lines.stream()
                    .map(l -> l.getUnitCost().multiply(BigDecimal.valueOf(l.getQuantityOrdered())).setScale(2, RoundingMode.HALF_UP))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            requestedPercent = TaxMath.percentFromFixedAmount(request.getDiscountFixedAmount(), rawSubtotal);
        } else {
            requestedPercent = request.getDiscountPercent();
        }
        po.setDiscountPercent(storeSettingsService.validateGlobalDiscount(requestedPercent));
        po.setDiscountType(discountType);
        po.setDiscountFixedAmount(discountType == GlobalDiscountType.FIXED_AMOUNT ? request.getDiscountFixedAmount() : null);
        po.getLines().addAll(lines);
    }
}
