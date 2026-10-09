package com.posapp.salesorder;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.common.GlobalDiscountType;
import com.posapp.common.TaxMath;
import com.posapp.tax.TaxRate;
import com.posapp.credit.CustomerCreditService;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.DocumentPdfService;
import com.posapp.salesorder.dto.FulfillSalesOrderRequest;
import com.posapp.salesorder.dto.SalesOrderDto;
import com.posapp.salesorder.dto.SalesOrderRequest;
import com.posapp.settings.StoreSettingsService;
import com.posapp.warehouse.LocationType;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.branch.BranchService;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Add Sale order... So that we can have POS & Sales" — a back-office order-taking flow entirely
 * separate from POS checkout ({@code com.posapp.order.OrderService}), deliberately mirroring
 * {@code com.posapp.inventory.PurchaseOrderService}'s DRAFT -&gt; locked -&gt; fulfilled shape on
 * the sales side (see {@link SalesOrderStatus}'s javadoc for the exact lifecycle). No
 * register/cashier session is involved anywhere in this class.
 *
 * <p>Confirming an order (DRAFT -&gt; CONFIRMED) is the moment its whole amount becomes something
 * the customer owes — {@link #confirm} stamps {@code dueDate} the same way a POS credit sale does
 * ({@code completedAt}/{@code orderDate} + the customer's {@code defaultCreditTermDays}, when
 * set). From that point on this order's outstanding amount is visible everywhere a POS credit
 * sale's is — Outstanding/Overdue Credit reports, the Customer Statement, the Record Payment
 * screen's allocation table — via {@code CustomerCreditService}'s own widened queries, not
 * anything in this class.
 *
 * <p>Unlike a POS credit sale, confirming does <em>not</em> require {@code Customer.creditEnabled}
 * or check {@code Customer.creditLimit} (Task: "The rules of enabling credit on customer should
 * only be required in POS Module not in Sale Module" — the POS module writes to a different table,
 * {@code sale_order}, than this Sales module's own {@code sales_order}, and the user wants the two
 * kept independent). A Sales Order can be confirmed for any customer, including one with credit
 * disabled or no limit set (e.g. the seeded, always-non-credit "Walk-in Customer" — see {@code
 * Customer.walkIn}'s javadoc) — the order's amount is simply recorded as owed, with no cap. Only
 * {@code OrderService.checkout} (the POS side) still gates on those two fields, via {@code
 * CustomerCreditService.assertWithinLimit}, which this class deliberately never calls.
 *
 * <p>Only {@link #fulfillLines} ever touches {@code Product.stockQuantity} (via {@link
 * CatalogService#adjustStock}, gated by the same store-wide "Allow negative stock" Inventory
 * setting {@code OrderService.validateStockAvailability} already enforces at POS checkout) —
 * creating, editing or confirming an order never does, since committing to a sale isn't the same
 * as having handed the goods over yet.
 */
@Service
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final CatalogService catalogService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    private final CustomerCreditService customerCreditService;
    private final SalesOrderFulfillmentRepository salesOrderFulfillmentRepository;
    private final AccountingService accountingService;
    private final DocumentPdfService documentPdfService;
    /** Multi-warehouse phase 3 — resolves/validates the location a fulfilment leaves from. */
    private final WarehouseService warehouseService;
    /** Validates the branch chosen on an order. */
    private final BranchService branchService;
    /** Stock promised on other confirmed orders. */
    private final StockReservationService stockReservationService;
    /** Serial numbers chosen when fulfilling. */
    private final com.posapp.tracking.TrackingService trackingService;

    public SalesOrderService(SalesOrderRepository salesOrderRepository,
                              CustomerRepository customerRepository,
                              CatalogService catalogService,
                              StoreSettingsService storeSettingsService,
                              AuditLogService auditLogService,
                              CustomerCreditService customerCreditService,
                              SalesOrderFulfillmentRepository salesOrderFulfillmentRepository,
                              AccountingService accountingService,
                              DocumentPdfService documentPdfService,
                              WarehouseService warehouseService,
                              BranchService branchService,
                              StockReservationService stockReservationService,
                              com.posapp.tracking.TrackingService trackingService) {
        this.trackingService = trackingService;
        this.stockReservationService = stockReservationService;
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.catalogService = catalogService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
        this.customerCreditService = customerCreditService;
        this.salesOrderFulfillmentRepository = salesOrderFulfillmentRepository;
        this.accountingService = accountingService;
        this.documentPdfService = documentPdfService;
        this.warehouseService = warehouseService;
        this.branchService = branchService;
    }

    /**
     * Multi-warehouse phase 3 — the location a fulfilment takes stock from. With the store's
     * multi-warehouse setting off, nothing changes: no location means the primary warehouse's normal
     * stock, and a chosen one is still accepted (it must be an active SALES location). With it on,
     * the same rule the POS applies holds — "no sale can be made at the default location/Main
     * warehouse" — so a SALES location outside the Main warehouse is required. Null return = legacy
     * product-level stock.
     */
    WarehouseLocation resolveFulfilmentLocation(Long locationId) {
        boolean multiWarehouse = storeSettingsService.getSettingsEntity().isMultiWarehouseEnabled();
        if (locationId == null) {
            if (multiWarehouse) {
                throw ApiException.badRequest("Multi-warehouse is enabled — choose the warehouse location this order is fulfilled from (the Main warehouse can't be used for sales).");
            }
            return null;
        }
        WarehouseLocation location = warehouseService.getLocationOrThrow(locationId);
        if (!location.isActive() || location.getWarehouse() == null || !location.getWarehouse().isActive()) {
            throw ApiException.badRequest("That location is inactive and cannot be fulfilled from");
        }
        if (location.getType() != LocationType.SALES) {
            throw ApiException.badRequest("Orders can only be fulfilled from a Sales location — '" + location.getName() + "' is " + location.getType());
        }
        if (multiWarehouse && location.getWarehouse().isPrimary()) {
            throw ApiException.badRequest("Multi-warehouse is enabled — no sale can be made from the Main warehouse. Choose a location in another warehouse.");
        }
        return location;
    }

    /**
     * Task 3: "Sale Order Return" — every {@code SalesOrderDto} now carries its own paid/
     * outstanding/returned/refund-due figures, computed the same way {@code
     * PurchaseOrderService.toDto} already does on the purchasing side via {@code
     * SupplierPaymentService}. Safe to depend on {@link CustomerCreditService} here even though
     * it reads Sales Orders back (via {@code SalesOrderRepository}, not this service) — see that
     * class's own javadoc: it never injects a {@code SalesOrderService} bean, so there is no
     * circular Spring dependency.
     */
    private SalesOrderDto toDto(SalesOrder so) {
        BigDecimal total = totalAmount(so);
        return SalesOrderDto.from(so, total, subtotalAmount(so), taxAmount(so), discountAmount(so),
                customerCreditService.amountPaidForSalesOrder(so),
                customerCreditService.outstandingForSalesOrder(so),
                customerCreditService.returnedAmountForSalesOrder(so),
                customerCreditService.refundDueForSalesOrder(so),
                fulfilledFromLabels(so));
    }

    /** Distinct "Warehouse / Location" labels of this order's fulfilments, oldest first; fulfilments that recorded no location (older ones, or from the primary warehouse's normal stock) contribute nothing. */
    private List<String> fulfilledFromLabels(SalesOrder so) {
        if (so.getId() == null) {
            return List.of();
        }
        return salesOrderFulfillmentRepository.findBySalesOrderOrderByCreatedAtAsc(so).stream()
                .filter(f -> f.getLocationName() != null)
                .map(f -> (f.getWarehouseName() != null ? f.getWarehouseName() + " / " : "") + f.getLocationName())
                .distinct()
                .toList();
    }

    private static BigDecimal lineGrossBeforeDiscount(SalesOrderLine line) {
        return line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantityOrdered())).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fourier: "Same for discount ... it will be per line", then "Except POS other module can
     * have global discount ... Either on line or global" — the line's gross after whichever
     * discount actually applies (this line's own PER_LINE value, or the order's uniform GLOBAL
     * value — see {@code TaxMath.effectiveDiscountPercent}) is knocked off; this is what tax is
     * actually computed on, same "discount before tax" ordering OrderCalculator.lineTotal
     * already uses for POS.
     */
    private static BigDecimal lineGross(BigDecimal headerDiscountPercent, SalesOrderLine line) {
        BigDecimal discountPercent = TaxMath.effectiveDiscountPercent(headerDiscountPercent, line.getDiscountPercent());
        return TaxMath.applyDiscount(lineGrossBeforeDiscount(line), discountPercent);
    }

    private static BigDecimal lineDiscountAmount(BigDecimal headerDiscountPercent, SalesOrderLine line) {
        return lineGrossBeforeDiscount(line).subtract(lineGross(headerDiscountPercent, line)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal lineTax(BigDecimal headerDiscountPercent, SalesOrderLine line) {
        return TaxMath.taxOnAmount(lineGross(headerDiscountPercent, line), line.getTaxRatePercent(), Boolean.TRUE.equals(line.getTaxInclusive()));
    }

    private static BigDecimal lineNet(BigDecimal headerDiscountPercent, SalesOrderLine line) {
        return TaxMath.netOfTax(lineGross(headerDiscountPercent, line), lineTax(headerDiscountPercent, line), Boolean.TRUE.equals(line.getTaxInclusive()));
    }

    /** Fourier: "Same for discount ... it will be per line" — sum of every line's own discount (PER_LINE or GLOBAL, whichever applies), purely informational (already folded into subtotalAmount/totalAmount above, via lineGross). Zero whenever discount is off or no line/order has one. */
    public static BigDecimal discountAmount(SalesOrder so) {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderLine line : so.getLines()) {
            total = total.add(lineDiscountAmount(so.getDiscountPercent(), line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Sum of every line's pre-tax (net) contribution — a plain static helper (not an instance
     * method) so {@code CustomerCreditService} can call it (via {@link #totalAmount}) without
     * needing a whole {@code SalesOrderService} bean injected just for this one calculation.
     */
    public static BigDecimal subtotalAmount(SalesOrder so) {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderLine line : so.getLines()) {
            total = total.add(lineNet(so.getDiscountPercent(), line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Sum of every line's own tax — zero when no line has a tax rate assigned (see SalesOrderLine.taxRatePercent's javadoc). */
    public static BigDecimal taxAmount(SalesOrder so) {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderLine line : so.getLines()) {
            total = total.add(lineTax(so.getDiscountPercent(), line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
     * subtotalAmount + taxAmount (previously just the sum of every line's {@code quantityOrdered
     * * unitPrice}, with no tax at all). Still a plain static helper so {@code
     * CustomerCreditService} can call it without needing a whole {@code SalesOrderService} bean.
     */
    public static BigDecimal totalAmount(SalesOrder so) {
        return subtotalAmount(so).add(taxAmount(so)).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<SalesOrderDto> listAll() {
        return salesOrderRepository.findAllByOrderByOrderDateDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SalesOrderDto getById(Long id) {
        SalesOrder so = getEntityOrThrow(id);
        return toDto(so);
    }

    /** Fourier: "add print (PDF with A4)" — an A4 PDF of this Sale Order to open in a new tab and print, same shape as InvoiceService.pdf. */
    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        SalesOrder so = getEntityOrThrow(id);
        return documentPdfService.salesOrderPdf(toDto(so), storeSettingsService.getSettingsEntity(),
                so.getBranchId() != null ? branchService.receiptInfo(so.getBranchId()) : null);
    }

    @Transactional
    public SalesOrderDto createDraft(SalesOrderRequest request) {
        SalesOrder so = new SalesOrder();
        so.setCustomer(getCustomerOrThrow(request.getCustomerId()));
        // Own "SO-" prefix — see SalesOrder's class javadoc for why this never collides with
        // PurchaseOrder's "PO-" or the POS SaleOrder's "POS-"-style invoice numbers.
        applyRequest(so, request);
        // Numbered after applyRequest so the chosen branch's document prefix can be applied.
        so.setOrderNumber(branchService.prefixedNumber(so.getBranchId(), "SO-" + System.currentTimeMillis()));
        SalesOrder saved = salesOrderRepository.save(so);
        auditLogService.record("SALES_ORDER_CREATED",
                "Created sales order " + saved.getOrderNumber() + " for " + saved.getCustomer().getName()
                        + " (" + saved.getLines().size() + " line" + (saved.getLines().size() == 1 ? "" : "s") + ")",
                "SalesOrder", saved.getId());
        return toDto(saved);
    }

    /** Only a DRAFT can still be edited — see SalesOrderStatus's javadoc. */
    @Transactional
    public SalesOrderDto updateDraft(Long id, SalesOrderRequest request) {
        SalesOrder so = getEntityOrThrow(id);
        if (so.getStatus() != SalesOrderStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft sales order can be edited — " + so.getOrderNumber() + " is " + so.getStatus());
        }
        so.setCustomer(getCustomerOrThrow(request.getCustomerId()));
        so.getLines().clear();
        applyRequest(so, request);
        SalesOrder saved = salesOrderRepository.save(so);
        auditLogService.record("SALES_ORDER_UPDATED", "Updated sales order " + saved.getOrderNumber(), "SalesOrder", saved.getId());
        return toDto(saved);
    }

    /**
     * DRAFT -&gt; CONFIRMED: locks the order to further line edits and stamps {@code dueDate} — see
     * class javadoc for why this deliberately does not check {@code Customer.creditEnabled}/{@code
     * creditLimit} the way POS checkout does.
     */
    @Transactional
    public SalesOrderDto confirm(Long id, boolean overrideShortage) {
        SalesOrder so = getEntityOrThrow(id);
        if (so.getStatus() != SalesOrderStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft sales order can be confirmed — " + so.getOrderNumber() + " is " + so.getStatus());
        }
        // Confirming reserves the ordered quantities. Warn (409) when what is available — sellable stock minus
        // what other confirmed orders already hold — doesn't cover this order; an admin can confirm anyway.
        java.util.Map<Long, Integer> needed = new java.util.LinkedHashMap<>();
        java.util.Map<Long, Product> neededProducts = new java.util.LinkedHashMap<>();
        for (SalesOrderLine line : so.getLines()) {
            Product p = line.getProduct();
            if (p == null || !p.isTrackStock() || p.isService()) continue;
            needed.merge(p.getId(), line.getQuantityOrdered() - line.getQuantityFulfilled(), Integer::sum);
            neededProducts.putIfAbsent(p.getId(), p);
        }
        StringBuilder shortages = new StringBuilder();
        for (java.util.Map.Entry<Long, Integer> e : needed.entrySet()) {
            Product p = neededProducts.get(e.getKey());
            int available = stockReservationService.available(p);
            if (e.getValue() > available) {
                if (shortages.length() > 0) shortages.append("; ");
                shortages.append(p.getName()).append(": ").append(Math.max(available, 0)).append(" available, ").append(e.getValue()).append(" ordered");
            }
        }
        if (shortages.length() > 0 && !overrideShortage) {
            throw ApiException.conflict("Not enough unreserved stock for " + shortages
                    + ". Confirm anyway to reserve it regardless (stock must arrive before it can be delivered).");
        }
        if (shortages.length() > 0) {
            auditLogService.record("SALES_ORDER_RESERVATION_OVERRIDE",
                    "Confirmed sales order " + so.getOrderNumber() + " with insufficient available stock (" + shortages + ")",
                    "SalesOrder", so.getId());
        }
        BigDecimal total = totalAmount(so);
        Customer customer = so.getCustomer();
        if (customer.getDefaultCreditTermDays() != null) {
            so.setDueDate(LocalDate.now().plusDays(customer.getDefaultCreditTermDays()));
        }
        so.setStatus(SalesOrderStatus.CONFIRMED);
        SalesOrder saved = salesOrderRepository.save(so);
        auditLogService.record("SALES_ORDER_CONFIRMED",
                "Confirmed sales order " + saved.getOrderNumber() + " for " + customer.getName() + " (" + total + " owed)",
                "SalesOrder", saved.getId());
        return toDto(saved);
    }

    /** Allowed any time before anything has actually been fulfilled — same "no undoing a real-world event" stance as PurchaseOrderService.cancel. */
    @Transactional
    public SalesOrderDto cancel(Long id) {
        SalesOrder so = getEntityOrThrow(id);
        if (so.getStatus() != SalesOrderStatus.DRAFT && so.getStatus() != SalesOrderStatus.CONFIRMED) {
            throw ApiException.badRequest("Cannot cancel sales order " + so.getOrderNumber() + " — it is " + so.getStatus());
        }
        so.setStatus(SalesOrderStatus.CANCELLED);
        SalesOrder saved = salesOrderRepository.save(so);
        auditLogService.record("SALES_ORDER_CANCELLED", "Cancelled sales order " + saved.getOrderNumber(), "SalesOrder", saved.getId());
        return toDto(saved);
    }

    /**
     * The stock-moving step: each entry bumps one line's {@code quantityFulfilled} and, in the
     * same transaction, decrements the matching product's live stock by the same amount via
     * {@link CatalogService#adjustStock} — gated by the store-wide "Allow negative stock" setting,
     * same messaging convention as {@code OrderService.validateStockAvailability}. Accepts a
     * partial fulfillment and can be called more than once against the same order.
     */
    @Transactional
    public SalesOrderDto fulfillLines(Long id, FulfillSalesOrderRequest request) {
        SalesOrder so = getEntityOrThrow(id);
        if (so.getStatus() != SalesOrderStatus.CONFIRMED && so.getStatus() != SalesOrderStatus.PARTIALLY_FULFILLED) {
            throw ApiException.badRequest("Cannot fulfill sales order " + so.getOrderNumber() + " — it is " + so.getStatus());
        }

        // Multi-warehouse phase 3: resolved once for the whole call; null = legacy primary stock.
        WarehouseLocation fulfilLocation = resolveFulfilmentLocation(request.getLocationId());

        boolean allowNegativeStock = storeSettingsService.getSettings().allowNegativeStock();
        if (!allowNegativeStock) {
            for (FulfillSalesOrderRequest.FulfillLineEntry entry : request.getLines()) {
                SalesOrderLine line = so.getLines().stream()
                        .filter(l -> l.getId().equals(entry.getLineId()))
                        .findFirst()
                        .orElseThrow(() -> ApiException.badRequest("Line " + entry.getLineId() + " does not belong to sales order " + so.getOrderNumber()));
                // Fourier: "Add track stock attribute on Product" — a non-tracked product can
                // always be fulfilled regardless of stockQuantity; see Product.trackStock's javadoc.
                if (line.getProduct() != null && line.getProduct().isTrackStock()) {
                    int onHand = fulfilLocation != null
                            ? warehouseService.getStockQuantity(line.getProduct(), fulfilLocation)
                            : line.getProduct().getStockQuantity();
                    // Stock promised to OTHER confirmed orders isn't free to take (this order's own outstanding is excluded).
                    int ownOutstanding = so.getLines().stream()
                            .filter(l -> l.getProduct() != null && l.getProduct().getId().equals(line.getProduct().getId()))
                            .mapToInt(l -> l.getQuantityOrdered() - l.getQuantityFulfilled()).sum();
                    int reservedByOthers = Math.max(0, stockReservationService.reserved(line.getProduct()) - ownOutstanding);
                    int available = onHand - reservedByOthers;
                    if (entry.getQuantityFulfilled() > available) {
                        throw ApiException.badRequest("Not enough stock for '" + line.getProductName() + "' — " + available
                                + " available" + (reservedByOthers > 0 ? " (" + reservedByOthers + " reserved for other sales orders)" : "")
                                + ", " + entry.getQuantityFulfilled() + " requested. Enable \"Allow negative stock\" in Inventory settings to fulfill anyway.");
                    }
                }
            }
        }

        StringBuilder fulfilledDescription = new StringBuilder();
        // Accounting's revenue/tax/COGS for exactly this call's fulfilled quantities — revenue
        // (now split into its net and tax portions, same as a POS sale — Fourier: "for purchases,
        // sales, invoices, bills the tax are not being captured") accrues for every fulfilled line
        // (the customer now owes for it), COGS/inventory relief only for trackStock lines (see
        // AccountingService.postSalesOrderFulfillment's own javadoc); cost is the product's live
        // costPrice at fulfillment time, since (unlike OrderLine) SalesOrderLine has no cost
        // snapshot of its own.
        BigDecimal netRevenueAmount = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal cogsAmount = BigDecimal.ZERO;
        for (FulfillSalesOrderRequest.FulfillLineEntry entry : request.getLines()) {
            SalesOrderLine line = so.getLines().stream()
                    .filter(l -> l.getId().equals(entry.getLineId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Line " + entry.getLineId() + " does not belong to sales order " + so.getOrderNumber()));
            int remaining = line.getQuantityOrdered() - line.getQuantityFulfilled();
            if (entry.getQuantityFulfilled() > remaining) {
                throw ApiException.badRequest("Cannot fulfill " + entry.getQuantityFulfilled() + " of '" + line.getProductName()
                        + "' — only " + remaining + " remaining (of " + line.getQuantityOrdered() + " ordered, " + line.getQuantityFulfilled() + " already fulfilled)");
            }
            line.setQuantityFulfilled(line.getQuantityFulfilled() + entry.getQuantityFulfilled());
            boolean inclusive = Boolean.TRUE.equals(line.getTaxInclusive());
            BigDecimal grossFulfilledBeforeDiscount = line.getUnitPrice().multiply(BigDecimal.valueOf(entry.getQuantityFulfilled()));
            // Fourier: "Same for discount ... it will be per line", then "Except POS other
            // module can have global discount ... Either on line or global" — the same
            // effective discountPercent (PER_LINE or GLOBAL, whichever applies) knocked off this
            // fulfilled slice too, before tax is computed on it, same ordering as lineGross/
            // lineTax above for the order's own totals.
            BigDecimal discountPercent = TaxMath.effectiveDiscountPercent(so.getDiscountPercent(), line.getDiscountPercent());
            BigDecimal grossFulfilled = TaxMath.applyDiscount(grossFulfilledBeforeDiscount, discountPercent);
            BigDecimal taxFulfilled = TaxMath.taxOnAmount(grossFulfilled, line.getTaxRatePercent(), inclusive);
            netRevenueAmount = netRevenueAmount.add(TaxMath.netOfTax(grossFulfilled, taxFulfilled, inclusive));
            taxAmount = taxAmount.add(taxFulfilled);
            // Fourier: "Add track stock attribute on Product" — a non-tracked product's stock is
            // never deducted at all, so quantityFulfilled still advances above but no stock move
            // (and no SalesOrderFulfillment row) is recorded for it; see Product.trackStock's javadoc.
            if (line.getProduct() != null && line.getProduct().isTrackStock() && entry.getQuantityFulfilled() != 0) {
                if (line.getProduct().getCostPrice() != null) {
                    cogsAmount = cogsAmount.add(
                            line.getProduct().getCostPrice().multiply(BigDecimal.valueOf(entry.getQuantityFulfilled())));
                }
                // Task 6: "Stock Movement History" — one structured, dated row per product
                // actually fulfilled, see SalesOrderFulfillment's own javadoc.
                int stockBefore = fulfilLocation != null
                        ? warehouseService.getStockQuantity(line.getProduct(), fulfilLocation)
                        : line.getProduct().getStockQuantity();
                java.util.Map<Long, List<String>> chosenSerials = null;
                if (com.posapp.tracking.TrackingService.modeOf(line.getProduct()) == com.posapp.tracking.TrackingMode.SERIAL
                        && entry.getSerialNumbers() != null && !entry.getSerialNumbers().isEmpty()) {
                    chosenSerials = java.util.Map.of(line.getProduct().getId(), entry.getSerialNumbers());
                }
                com.posapp.tracking.TrackingContext.begin(so.getOrderNumber(), chosenSerials);
                try {
                    catalogService.adjustStock(line.getProduct(), -entry.getQuantityFulfilled(), fulfilLocation);
                } finally {
                    com.posapp.tracking.TrackingContext.clear();
                }
                int stockAfter = fulfilLocation != null
                        ? warehouseService.getStockQuantity(line.getProduct(), fulfilLocation)
                        : line.getProduct().getStockQuantity();
                SalesOrderFulfillment fulfillment = new SalesOrderFulfillment();
                if (fulfilLocation != null) {
                    fulfillment.setLocation(fulfilLocation);
                    fulfillment.setLocationName(fulfilLocation.getName());
                    fulfillment.setWarehouseName(fulfilLocation.getWarehouse().getName());
                }
                fulfillment.setSalesOrder(so);
                fulfillment.setOrderNumber(so.getOrderNumber());
                fulfillment.setProduct(line.getProduct());
                fulfillment.setProductName(line.getProductName());
                fulfillment.setQuantityFulfilled(entry.getQuantityFulfilled());
                fulfillment.setStockBefore(stockBefore);
                fulfillment.setStockAfter(stockAfter);
                salesOrderFulfillmentRepository.save(fulfillment);
            }
            if (fulfilledDescription.length() > 0) fulfilledDescription.append(", ");
            fulfilledDescription.append(entry.getQuantityFulfilled()).append(" x ").append(line.getProductName());
        }

        boolean allFulfilled = so.getLines().stream().allMatch(l -> l.getQuantityFulfilled() >= l.getQuantityOrdered());
        boolean anyFulfilled = so.getLines().stream().anyMatch(l -> l.getQuantityFulfilled() > 0);
        so.setStatus(allFulfilled ? SalesOrderStatus.FULFILLED : anyFulfilled ? SalesOrderStatus.PARTIALLY_FULFILLED : so.getStatus());

        SalesOrder saved = salesOrderRepository.save(so);
        auditLogService.record("SALES_ORDER_FULFILLED",
                "Fulfilled " + fulfilledDescription + " against sales order " + saved.getOrderNumber() + " (now " + saved.getStatus() + ")",
                "SalesOrder", saved.getId());

        // Fourier: "all...Sale...module will be integrated when accounting is enabled" — posts
        // exactly this call's fulfilled revenue/tax/COGS; see AccountingService.postSalesOrderFulfillment.
        accountingService.postSalesOrderFulfillment(saved,
                netRevenueAmount.setScale(2, RoundingMode.HALF_UP), taxAmount.setScale(2, RoundingMode.HALF_UP),
                cogsAmount.setScale(2, RoundingMode.HALF_UP));

        return toDto(saved);
    }

    SalesOrder getEntityOrThrow(Long id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Sales order not found: " + id));
    }

    private Customer getCustomerOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
    }

    private void applyRequest(SalesOrder so, SalesOrderRequest request) {
        so.setExpectedDate(request.getExpectedDate());
        so.setNotes(request.getNotes());
        // Branch: an explicit choice wins; otherwise keep the existing one on an edit, head office on a new order.
        if (request.getBranchId() != null || so.getBranchId() == null) {
            so.setBranchId(branchService.resolveForDocument(request.getBranchId()));
        }
        List<SalesOrderLine> lines = request.getLines().stream().map(entry -> {
            Product product = catalogService.getProductOrThrow(entry.getProductId());
            SalesOrderLine line = new SalesOrderLine();
            line.setSalesOrder(so);
            line.setProduct(product);
            line.setProductName(product.getName());
            line.setQuantityOrdered(entry.getQuantityOrdered());
            line.setUnitPrice(entry.getUnitPrice() != null ? entry.getUnitPrice() : product.getUnitPrice());
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
            BigDecimal rawSubtotal = lines.stream().map(SalesOrderService::lineGrossBeforeDiscount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            requestedPercent = TaxMath.percentFromFixedAmount(request.getDiscountFixedAmount(), rawSubtotal);
        } else {
            requestedPercent = request.getDiscountPercent();
        }
        so.setDiscountPercent(storeSettingsService.validateGlobalDiscount(requestedPercent));
        so.setDiscountType(discountType);
        so.setDiscountFixedAmount(discountType == GlobalDiscountType.FIXED_AMOUNT ? request.getDiscountFixedAmount() : null);
        so.getLines().addAll(lines);
    }
}
