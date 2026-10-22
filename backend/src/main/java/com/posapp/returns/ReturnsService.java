package com.posapp.returns;

import com.posapp.apps.AppKey;
import com.posapp.apps.AppsService;
import com.posapp.apps.efris.EfrisSettingService;
import com.posapp.apps.efris.EfrisSubmissionService;
import com.posapp.apps.efris.dto.EfrisConnection;
import com.posapp.apps.efris.dto.EfrisInvoiceLine;
import com.posapp.apps.efris.dto.EfrisInvoicePayload;
import com.posapp.apps.efris.dto.EfrisSubmissionResult;
import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.order.OrderCalculator;
import com.posapp.order.OrderLine;
import com.posapp.order.OrderService;
import com.posapp.order.SaleOrder;
import com.posapp.paymentmethod.PaymentMethodService;
import com.posapp.register.Register;
import com.posapp.returns.dto.ProcessReturnRequest;
import com.posapp.returns.dto.SalesReturnDto;
import com.posapp.settings.StoreSettingsService;
import com.posapp.user.AppUser;
import com.posapp.warehouse.Warehouse;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.branch.BranchService;
import com.posapp.warehouse.WarehouseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReturnsService {

    private static final Logger log = LoggerFactory.getLogger(ReturnsService.class);

    /** Same RRA standard-rate fallback as OrderService.DEFAULT_EFRIS_TAX_CODE, duplicated here (not shared as a constant) because it's a single literal, not worth a cross-package dependency for. */
    private static final String DEFAULT_EFRIS_TAX_CODE = "B";

    private final SalesReturnRepository salesReturnRepository;
    private final SalesReturnLineRepository salesReturnLineRepository;
    private final OrderService orderService;
    private final CatalogService catalogService;
    private final PaymentMethodService paymentMethodService;
    private final AppsService appsService;
    private final EfrisSettingService efrisSettingService;
    private final EfrisSubmissionService efrisSubmissionService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    private final AccountingService accountingService;
    /** Fourier: "the terminal will be registered on warehouse" — resolves which warehouse a return restocks; see #resolveSellingWarehouse/#stockQuantityFor. */
    private final WarehouseService warehouseService;
    /** Applies the original sale's branch document prefix to the return number. */
    private final BranchService branchService;
    private final com.posapp.giftcards.GiftCardService giftCardService;
    private final com.posapp.loyalty.LoyaltyService loyaltyService;
    private final com.posapp.register.PosSettingsService posSettingsService;
    private final com.posapp.register.ManagerApprovalService approvalService;
    private final com.posapp.register.CashierSessionRepository cashierSessionRepository;

    public ReturnsService(SalesReturnRepository salesReturnRepository,
                           SalesReturnLineRepository salesReturnLineRepository,
                           OrderService orderService,
                           CatalogService catalogService,
                           PaymentMethodService paymentMethodService,
                           AppsService appsService,
                           EfrisSettingService efrisSettingService,
                           EfrisSubmissionService efrisSubmissionService,
                           StoreSettingsService storeSettingsService,
                           AuditLogService auditLogService,
                           AccountingService accountingService,
                           WarehouseService warehouseService,
                           BranchService branchService,
                           com.posapp.giftcards.GiftCardService giftCardService,
                           com.posapp.loyalty.LoyaltyService loyaltyService,
                           com.posapp.register.PosSettingsService posSettingsService,
                           com.posapp.register.ManagerApprovalService approvalService,
                           com.posapp.register.CashierSessionRepository cashierSessionRepository) {
        this.posSettingsService = posSettingsService;
        this.approvalService = approvalService;
        this.cashierSessionRepository = cashierSessionRepository;
        this.giftCardService = giftCardService;
        this.loyaltyService = loyaltyService;
        this.salesReturnRepository = salesReturnRepository;
        this.salesReturnLineRepository = salesReturnLineRepository;
        this.orderService = orderService;
        this.catalogService = catalogService;
        this.paymentMethodService = paymentMethodService;
        this.appsService = appsService;
        this.efrisSettingService = efrisSettingService;
        this.efrisSubmissionService = efrisSubmissionService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
        this.accountingService = accountingService;
        this.warehouseService = warehouseService;
        this.branchService = branchService;
    }

    /**
     * Fourier confirmed: a return restocks the same warehouse the original sale deducted from,
     * not always the primary warehouse like a purchase. Resolved from the original order's
     * register — see {@code OrderService#resolveSellingWarehouse}'s identical reasoning (null
     * means "use the primary warehouse", same fallback {@code CatalogService#adjustStock}
     * already applies). Used only by EFRIS submission's register lookup now — every actual stock
     * operation below goes through {@link #resolveSellingLocation} instead.
     */
    private Warehouse resolveSellingWarehouse(SaleOrder order) {
        Register register = order != null && order.getSession() != null ? order.getSession().getRegister() : null;
        return register != null ? register.getWarehouse() : null;
    }

    /** Same shape as {@code OrderService#resolveSellingLocation} — duplicated rather than shared across packages, same convention as this file's own DEFAULT_EFRIS_TAX_CODE. Restocks the same location the original sale deducted from, resolved from the original order's register, not the return itself. Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — the warehouse-only fallback now resolves its SALES location specifically, via {@code WarehouseService#resolveFallbackSalesLocation}. */
    private WarehouseLocation resolveSellingLocation(SaleOrder order) {
        Register register = order != null && order.getSession() != null ? order.getSession().getRegister() : null;
        if (register == null) {
            return null;
        }
        WarehouseLocation location = register.getDefaultLocation();
        if (location == null) {
            Warehouse warehouse = register.getWarehouse();
            if (warehouse == null) {
                return null;
            }
            location = warehouseService.resolveFallbackSalesLocation(warehouse);
        }
        Warehouse locationWarehouse = location.getWarehouse();
        boolean isPrimaryCanonicalSalesLocation = locationWarehouse != null && locationWarehouse.isPrimary()
                && location.getId().equals(warehouseService.resolveCanonicalSalesLocation(locationWarehouse).getId());
        return isPrimaryCanonicalSalesLocation ? null : location;
    }

    /** Same shape as {@code OrderService#stockQuantityFor} — duplicated rather than shared across packages, same convention as this file's own DEFAULT_EFRIS_TAX_CODE. */
    private int stockQuantityFor(com.posapp.catalog.Product product, WarehouseLocation location) {
        if (location == null) {
            return product.getStockQuantity();
        }
        return warehouseService.getStockQuantity(product, location);
    }

    @Transactional(readOnly = true)
    public List<SalesReturnDto> listReturns() {
        return salesReturnRepository.findAllByOrderByReturnDateDesc().stream().map(SalesReturnDto::from).toList();
    }

    /** Returns with filters: text (invoice, return number, customer, reason), refund method, register and the day returned. */
    @Transactional(readOnly = true)
    public List<SalesReturnDto> filteredReturns(String q, String method, Long registerId, java.time.LocalDate from, java.time.LocalDate to) {
        return salesReturnRepository.findAllByOrderByReturnDateDesc().stream()
                .filter(r -> com.posapp.common.ListPaging.between(r.getReturnDate(), from, to))
                .filter(r -> method == null || method.isBlank() || method.equalsIgnoreCase(r.getRefundMethod()))
                .map(SalesReturnDto::from)
                .filter(d -> registerId == null || registerId.equals(d.registerId()))
                .filter(d -> com.posapp.common.ListPaging.has(q, d.invoiceNumber(), d.returnNumber(), d.customerName(), d.reason()))
                .toList();
    }

    /** Task: "print out the return" — a single return's own detail, including its line items, for the return receipt. */
    @Transactional(readOnly = true)
    public SalesReturnDto getById(Long id) {
        SalesReturn salesReturn = salesReturnRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Return not found: " + id));
        return SalesReturnDto.from(salesReturn);
    }

    /**
     * Task: "on the receipt which was returned, I should mark it as returned" — every order-line
     * id on this order that has ever had anything returned against it, mapped to the total
     * quantity returned across every separate return visit. The sale receipt merges this in
     * client-side rather than the backend embedding it onto OrderDto — order/checkout/receipt DTOs
     * live in the `order` package, which `returns` already depends on (ReturnsService needs
     * OrderService); adding a dependency the other way, from `order` back into `returns`, would
     * create the same kind of circular package dependency this codebase has deliberately avoided
     * everywhere else (see CashierSession.activeOrderId's javadoc for the same reasoning applied
     * to a different pair of packages).
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> returnedQuantitiesForOrder(Long orderId) {
        return salesReturnLineRepository.findByOrderId(orderId).stream()
                .collect(Collectors.groupingBy(l -> l.getOrderLine().getId(), Collectors.summingInt(SalesReturnLine::getQuantity)));
    }

    @Transactional(readOnly = true)
    public BigDecimal sumCashRefundsForSession(Long sessionId) {
        Set<String> cashCodes = paymentMethodService.listCashCodes();
        if (cashCodes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return salesReturnRepository.sumCashRefundsForSession(sessionId, cashCodes);
    }

    /** The returns that count against one shift's drawer and report. */
    @Transactional(readOnly = true)
    public List<SalesReturn> returnsForSession(Long sessionId) {
        return salesReturnRepository.findForSession(sessionId);
    }

    @Transactional
    public SalesReturnDto processReturn(ProcessReturnRequest request, AppUser caller) {
        return processReturn(request, caller, false);
    }

    /** The shift whose drawer pays a refund made now: the caller's own open shift, else the sale's own shift if it is still open, else none. */
    private com.posapp.register.RegisterSession drawerFor(SaleOrder order, AppUser caller) {
        if (caller != null) {
            for (com.posapp.register.CashierSession cs : cashierSessionRepository.findByCashierIdAndStatusOrderByStartedAtDesc(caller.getId(), com.posapp.register.CashierSessionStatus.OPEN)) {
                if (cs.getRegisterSession() != null && cs.getRegisterSession().getStatus() == com.posapp.register.SessionStatus.OPEN) return cs.getRegisterSession();
            }
        }
        com.posapp.register.RegisterSession own = order.getSession();
        return own != null && own.getStatus() == com.posapp.register.SessionStatus.OPEN ? own : null;
    }

    /**
     * {@code voidMode}: the refund of a voided sale (the void already checked its own rules and approval, so the return window and limit do not apply again).
     */
    private SalesReturnDto processReturn(ProcessReturnRequest request, AppUser caller, boolean voidMode) {
        SaleOrder order = orderService.getCompletedOrderById(request.getOrderId());
        String refundMethod = paymentMethodService.validateActiveCodeOrThrow(request.getRefundMethod());
        com.posapp.register.PosSettings cfg = posSettingsService.entity();
        String why = request.getReason() == null ? "" : request.getReason().trim();
        if (!voidMode && cfg.isReturnReasonRequired() && why.isEmpty()) throw ApiException.badRequest("Choose the reason for the return");
        if (why.length() > 200) throw ApiException.badRequest("The reason is too long (200 characters at most)");
        if (order.getVoidedAt() != null) throw ApiException.badRequest("That sale was voided; it has already been refunded");
        String approvedBy = null;
        String needs = null;
        if (!voidMode && cfg.getReturnWindowDays() > 0 && order.getCompletedAt() != null
                && order.getCompletedAt().isBefore(Instant.now().minus(java.time.Duration.ofDays(cfg.getReturnWindowDays())))) {
            needs = "This sale is more than " + cfg.getReturnWindowDays() + " days old, outside the return window";
        }

        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setOrder(order);
        salesReturn.setRefundMethod(refundMethod);
        salesReturn.setReason(why.isEmpty() ? null : why);
        com.posapp.register.RegisterSession drawer = drawerFor(order, caller);
        salesReturn.setRegisterSessionId(drawer == null ? null : drawer.getId());
        salesReturn.setProcessedBy(caller == null ? null : caller.getUsername());
        salesReturn.setVoidReturn(voidMode);
        // This return's own EFRIS erp_code — same "POS-" + millis shape OrderService uses for
        // invoiceNumber, assigned right away since (unlike an order) a return has no draft/open
        // state to defer this to. See SalesReturn.returnNumber's javadoc.
        salesReturn.setReturnNumber(branchService.prefixedNumber(order.getBranchId(), "POS-RET-" + System.currentTimeMillis()));

        BigDecimal subtotalRefund = BigDecimal.ZERO;
        BigDecimal taxRefund = BigDecimal.ZERO;
        // Accounting's Inventory-restocked/COGS-reversal figure — only for lines this loop actually
        // restocks (trackStock), using the same OrderLine.unitCost snapshot the Profit report
        // already reads; see AccountingService.postPosReturn's own javadoc.
        BigDecimal cogsReversal = BigDecimal.ZERO;
        // Fourier confirmed: restocks the same location the original sale deducted from.
        WarehouseLocation sellingLocation = resolveSellingLocation(order);
        // Warehouse filter on Stock Movement History: null = Main warehouse (see SalesReturn.stockWarehouseId).
        salesReturn.setStockWarehouseId(sellingLocation != null ? sellingLocation.getWarehouse().getId() : null);
        // A return always belongs to the branch the original sale was made at.
        salesReturn.setBranchId(order.getBranchId());

        for (ProcessReturnRequest.ReturnLineEntry entry : request.getLines()) {
            OrderLine line = order.getLines().stream()
                    .filter(l -> l.getId().equals(entry.getOrderLineId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Line " + entry.getOrderLineId() + " does not belong to this order"));

            int alreadyReturned = salesReturnLineRepository.sumReturnedQuantityForLine(line.getId());
            int remaining = line.getQuantity() - alreadyReturned;
            if (entry.getQuantity() > remaining) {
                throw ApiException.badRequest("Cannot return " + entry.getQuantity() + " of '" + line.getDescription()
                        + "' — only " + remaining + " remaining (of " + line.getQuantity() + " sold, " + alreadyReturned + " already returned)");
            }

            BigDecimal discountFactor = BigDecimal.ONE.subtract(
                    line.effectiveDiscountPercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
            BigDecimal lineRefund = line.getUnitPrice()
                    .multiply(BigDecimal.valueOf(entry.getQuantity()))
                    .multiply(discountFactor)
                    .setScale(2, RoundingMode.HALF_UP);

            // Tax refund uses this line's own snapshotted rate and inclusive/exclusive flag (see
            // OrderLine's javadoc) rather than a single store-wide rate — different lines on the
            // same sale can carry different tax rates, or none at all, and (as of the inclusive-tax
            // pass) some may have had tax folded into the price rather than added on top.
            // OrderCalculator.taxOnAmount applies the same rule here as OrderCalculator.lineTax
            // does for a whole line, just against this partial (returned) quantity's slice of it.
            BigDecimal lineTaxRefund = OrderCalculator.taxOnAmount(
                    lineRefund, line.getTaxRatePercent(), Boolean.TRUE.equals(line.getTaxInclusive()));
            taxRefund = taxRefund.add(lineTaxRefund);

            // For an inclusive-tax line, lineRefund already has tax baked in — the "subtotal"
            // (pre-tax) share of the refund is lineRefund minus what taxOnAmount just extracted, not
            // lineRefund itself (which would double-count the tax once taxRefund is added back on
            // in refundTotal below).
            BigDecimal lineNetRefund = Boolean.TRUE.equals(line.getTaxInclusive())
                    ? lineRefund.subtract(lineTaxRefund).setScale(2, RoundingMode.HALF_UP)
                    : lineRefund;
            subtotalRefund = subtotalRefund.add(lineNetRefund);

            SalesReturnLine returnLine = new SalesReturnLine();
            returnLine.setSalesReturn(salesReturn);
            returnLine.setOrderLine(line);
            returnLine.setQuantity(entry.getQuantity());
            // The same post-discount, pre-tax-extraction figure already computed above for the
            // return's own subtotal/tax split — snapshotted here too so the return receipt can
            // show a per-line amount without recomputing this line's pricing/tax a second time.
            returnLine.setRefundAmount(lineRefund);
            salesReturn.getLines().add(returnLine);

            // Fourier: "Add track stock attribute on Product" — a non-tracked product's sale never
            // deducted stock, so returning it doesn't restock anything either; see
            // Product.trackStock's javadoc.
            if (line.getProduct() != null && line.getProduct().isTrackStock()) {
                // Task 6: "Stock Movement History" — snapshot before/after, see SalesReturnLine's javadoc.
                int stockBefore = stockQuantityFor(line.getProduct(), sellingLocation);
                catalogService.adjustStock(line.getProduct(), entry.getQuantity(), sellingLocation);
                returnLine.setStockBefore(stockBefore);
                returnLine.setStockAfter(stockQuantityFor(line.getProduct(), sellingLocation));
                if (line.getUnitCost() != null) {
                    cogsReversal = cogsReversal.add(
                            line.getUnitCost().multiply(BigDecimal.valueOf(entry.getQuantity())));
                }
            }
        }
        cogsReversal = cogsReversal.setScale(2, RoundingMode.HALF_UP);

        salesReturn.setRefundTotal(subtotalRefund.add(taxRefund));

        if (!voidMode && needs == null && cfg.getReturnApprovalOver().signum() > 0 && salesReturn.getRefundTotal().compareTo(cfg.getReturnApprovalOver()) > 0) {
            needs = "A refund of " + salesReturn.getRefundTotal().setScale(2, RoundingMode.HALF_UP).toPlainString() + " is above the limit of "
                    + cfg.getReturnApprovalOver().setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        if (needs != null) {
            if (com.posapp.register.ManagerApprovalService.canApprove(caller, com.posapp.register.ManagerApprovalService.Kind.VOID_RETURN)) {
                approvedBy = caller.getUsername();
            } else {
                if (request.getApproverPin() == null || request.getApproverPin().isBlank()) {
                    throw ApiException.forbidden("Manager approval needed: " + needs + ". A manager must sign in or type their PIN.");
                }
                approvedBy = approvalService.approverByPin(request.getApproverPin(), com.posapp.register.ManagerApprovalService.Kind.VOID_RETURN).getUsername();
            }
        }
        salesReturn.setApprovedBy(approvedBy);

        SalesReturn saved = salesReturnRepository.save(salesReturn);
        splitRefund(saved, order);
        // Task: "Implement an audit log of who did what and when" — processing a return is a curated action, see AuditLogService's javadoc.
        String originalInvoice = order.getInvoiceNumber() != null ? order.getInvoiceNumber() : ("#" + order.getId());
        auditLogService.record("RETURN_PROCESSED",
                "Processed return " + saved.getReturnNumber() + " for order " + originalInvoice + " (refund " + saved.getRefundTotal() + ")",
                "SalesReturn", saved.getId());

        // Fourier: "all POS...module will be integrated when accounting is enabled" — see
        // AccountingService.postPosReturn's own javadoc; refundMethodIsCredit reuses the same
        // admin-configured credit-code set OrderService.completeCheckout checks at sale time.
        boolean refundMethodIsCredit = paymentMethodService.listCreditCodes().contains(refundMethod);
        accountingService.postPosReturn(saved, refundMethod, refundMethodIsCredit, subtotalRefund, taxRefund, cogsReversal);

        return SalesReturnDto.from(saved);
    }

    /**
     * Voiding a completed sale (a wrong sale caught at the till): the whole sale is refunded through the normal return, so stock goes back, Accounting is reversed, gift cards
     * and points are given back and the EFRIS credit note is made exactly as for a return; the sale stays on record, marked voided with who, why and who approved.
     * By default only while the sale's shift is open (afterwards it is a return) and only with a manager's approval.
     */
    @Transactional
    public SalesReturnDto voidSale(com.posapp.returns.dto.VoidSaleRequest request, AppUser caller) {
        SaleOrder order = orderService.getCompletedOrderById(request.getOrderId());
        com.posapp.register.PosSettings cfg = posSettingsService.entity();
        String why = request.getReason() == null ? "" : request.getReason().trim();
        if (why.isEmpty()) throw ApiException.badRequest("Say why the sale is being voided");
        if (why.length() > 200) throw ApiException.badRequest("The reason is too long (200 characters at most)");
        if (order.getVoidedAt() != null) throw ApiException.badRequest("That sale is already voided");
        if (!salesReturnRepository.findByOrderIdOrderByReturnDateAsc(order.getId()).isEmpty()) {
            throw ApiException.badRequest("Part of that sale was already returned; process a return for the rest instead");
        }
        if (order.getCreditAmount() != null && order.getCreditAmount().signum() > 0) {
            throw ApiException.badRequest("That sale was partly sold on credit; use a return so the customer's balance is dealt with");
        }
        if (order.getRoomChargeAmount() != null && order.getRoomChargeAmount().signum() > 0) {
            throw ApiException.badRequest("That sale was charged to a room; use a return so the room bill is dealt with");
        }
        if (cfg.isVoidSameShiftOnly() && (order.getSession() == null || order.getSession().getStatus() != com.posapp.register.SessionStatus.OPEN)) {
            throw ApiException.badRequest("That sale belongs to a shift that is already closed; process a return instead");
        }
        String approvedBy = null;
        if (cfg.isVoidRequiresApproval()) {
            if (com.posapp.register.ManagerApprovalService.canApprove(caller, com.posapp.register.ManagerApprovalService.Kind.VOID_RETURN)) {
                approvedBy = caller.getUsername();
            } else {
                if (request.getApproverPin() == null || request.getApproverPin().isBlank()) {
                    throw ApiException.forbidden("Manager approval needed: voiding a sale. A manager must sign in or type their PIN.");
                }
                approvedBy = approvalService.approverByPin(request.getApproverPin(), com.posapp.register.ManagerApprovalService.Kind.VOID_RETURN).getUsername();
            }
        }
        // how the money goes back: as asked, else the way most of it came in
        String method = request.getRefundMethod();
        if (method == null || method.isBlank()) {
            method = order.getPayments().stream().filter(p -> !com.posapp.order.Tenders.isSpecial(p.getMethod()))
                    .max(java.util.Comparator.comparing(com.posapp.order.Payment::getAmount)).map(com.posapp.order.Payment::getMethod).orElse(null);
            if (method == null) method = paymentMethodService.listCashCodes().stream().findFirst().orElseThrow(() -> ApiException.badRequest("Choose how to refund"));
        }
        ProcessReturnRequest full = new ProcessReturnRequest();
        full.setOrderId(order.getId());
        full.setRefundMethod(method);
        full.setReason("Void: " + why);
        List<ProcessReturnRequest.ReturnLineEntry> lines = new java.util.ArrayList<>();
        for (OrderLine l : order.getLines()) {
            ProcessReturnRequest.ReturnLineEntry e = new ProcessReturnRequest.ReturnLineEntry();
            e.setOrderLineId(l.getId());
            e.setQuantity(l.getQuantity());
            lines.add(e);
        }
        full.setLines(lines);
        SalesReturnDto done = processReturn(full, caller, true);
        orderService.markVoided(order.getId(), why, caller == null ? null : caller.getUsername(), approvedBy);
        SalesReturn saved = salesReturnRepository.findById(done.id()).orElseThrow();
        saved.setApprovedBy(approvedBy);
        salesReturnRepository.save(saved);
        auditLogService.record("SALE_VOIDED_AFTER_SALE", "Voided sale " + (order.getInvoiceNumber() == null ? "#" + order.getId() : order.getInvoiceNumber()) + " (" + done.refundTotal() + "): " + why
                + (approvedBy == null ? "" : " — approved by " + approvedBy), "SaleOrder", order.getId());
        return SalesReturnDto.from(saved);
    }

    /**
     * A return gives back, first, to what paid for the sale: the share of the refund that equals the share of the sale a gift card paid goes back onto the card (if it can still take it),
     * the share points paid goes back as points, and the rest is refunded the normal way. The points earned on the returned goods are taken back too. Rounding never lets the
     * parts add up to more than the refund.
     */
    private void splitRefund(SalesReturn saved, SaleOrder order) {
        BigDecimal refund = saved.getRefundTotal();
        BigDecimal total = com.posapp.order.OrderCalculator.total(order);
        if (refund.signum() <= 0 || total.signum() <= 0) return;
        BigDecimal giftPaid = BigDecimal.ZERO, loyaltyPaid = BigDecimal.ZERO;
        int pointsPaid = 0;
        for (com.posapp.order.Payment p : order.getPayments()) {
            if (com.posapp.order.Tenders.GIFT_CARD.equals(p.getMethod())) giftPaid = giftPaid.add(p.getAmount());
            if (com.posapp.order.Tenders.LOYALTY.equals(p.getMethod())) {
                loyaltyPaid = loyaltyPaid.add(p.getAmount());
                pointsPaid += p.getPoints() == null ? 0 : p.getPoints();
            }
        }
        BigDecimal giftBack = BigDecimal.ZERO;
        if (giftPaid.signum() > 0) {
            BigDecimal share = refund.multiply(giftPaid).divide(total, 2, RoundingMode.HALF_UP).min(refund);
            giftBack = giftCardService.refundToCards(order, share);
            saved.setGiftCardRefund(giftBack);
        }
        if (loyaltyPaid.signum() > 0 && pointsPaid > 0) {
            BigDecimal share = refund.multiply(loyaltyPaid).divide(total, 2, RoundingMode.HALF_UP).min(refund.subtract(giftBack));
            share = share.min(loyaltyPaid.subtract(salesReturnRepository.sumLoyaltyRefundForOrder(order.getId(), saved.getId())));
            if (share.signum() > 0) {
                int pts = BigDecimal.valueOf(pointsPaid).multiply(share).divide(loyaltyPaid, 0, RoundingMode.HALF_UP).intValue();
                pts = Math.min(pts, pointsPaid - salesReturnRepository.sumPointsRefundForOrder(order.getId(), saved.getId()).intValue());
                if (pts > 0) {
                    loyaltyService.refundPoints(order, pts, saved.getId());
                    saved.setLoyaltyRefund(share);
                    saved.setPointsRefund(pts);
                }
            }
        }
        loyaltyService.reverseEarned(order, refund.divide(total, 6, RoundingMode.HALF_UP), saved.getId());
    }

    /**
     * Fourier's own follow-up: "The return will also integrate with EFRIS ... the only change
     * are: 1. instead of sending 'transaction_type':'S' send 'transaction_type':'R' 2. instead of
     * sending 'erp_code_ref':'0' send 'erp_code_ref': value of erp_code of the original receipt."
     * Mirrors {@code OrderService.submitToEfrisIfApplicable} exactly — same guards, same
     * "return still completes, flagged for review" policy (a failed/unreachable EFRIS submission
     * never blocks or undoes a return that already happened), same idempotency check (an
     * already-fiscalized return is never resubmitted) — just building the payload from a {@link
     * SalesReturn} instead of a {@code SaleOrder}. Called right after {@link #processReturn}
     * creates the return (see {@code ReturnsController}), and again by its own {@code
     * /returns/{id}/efris/retry} endpoint, same "retry is just calling this again" shape as sales.
     */
    @Transactional
    public SalesReturnDto submitToEfrisIfApplicable(Long returnId) {
        SalesReturn salesReturn = salesReturnRepository.findById(returnId)
                .orElseThrow(() -> ApiException.notFound("Return not found: " + returnId));
        try {
            if (salesReturn.getSdcId() != null) {
                // Already fiscalized — a repeat call (e.g. two retry clicks) must never resubmit.
                return SalesReturnDto.from(salesReturn);
            }
            if (!appsService.isEnabled(AppKey.EFRIS_RW)) {
                return SalesReturnDto.from(salesReturn);
            }
            SaleOrder order = salesReturn.getOrder();
            Register register = order != null && order.getSession() != null ? order.getSession().getRegister() : null;
            if (register == null) {
                return SalesReturnDto.from(salesReturn);
            }
            Optional<EfrisConnection> connection = efrisSettingService.getConnectionForRegister(register.getId());
            if (connection.isEmpty()) {
                // This terminal has no EFRIS URL configured yet — not an error, just nothing to do.
                return SalesReturnDto.from(salesReturn);
            }

            EfrisSubmissionResult result = efrisSubmissionService.submit(connection.get(), buildEfrisPayload(salesReturn));
            salesReturn.setEfrisSubmittedAt(Instant.now());
            salesReturn.setEfrisResponse(result.rawResponse());
            if (result.success()) {
                salesReturn.setSdcId(result.sdcId());
                salesReturn.setSdcDate(result.sdcDate());
                salesReturn.setSdcReceiptNumber(result.sdcReceiptNumber());
                salesReturn.setInternalData(result.internalData());
                salesReturn.setSignatureData(result.signatureData());
                salesReturn.setCisNumber(result.cisNumber());
                salesReturn.setCisDate(result.cisDate());
                salesReturn.setMrcData(result.mrcData());
                salesReturn.setEfrisError(null);
            } else {
                salesReturn.setEfrisError(result.errorMessage());
            }
            return SalesReturnDto.from(salesReturnRepository.save(salesReturn));
        } catch (Exception e) {
            // Belt-and-suspenders, same as OrderService.submitToEfrisIfApplicable: nothing here may
            // ever propagate and roll back a return that already completed.
            log.warn("Unexpected error submitting return {} to EFRIS", returnId, e);
            salesReturn.setEfrisSubmittedAt(Instant.now());
            salesReturn.setEfrisError("Unexpected error submitting to EFRIS: " + e.getMessage());
            return SalesReturnDto.from(salesReturnRepository.save(salesReturn));
        }
    }

    /**
     * Reduces a processed return to exactly what EFRIS's {@code api.invoice/create_api} needs —
     * the return's counterpart to {@code OrderService.buildEfrisPayload}. Reuses {@code
     * orderService.resolvePaymentMode}/{@code orderService.resolveProductCode} rather than
     * duplicating them (both were made public on OrderService specifically for this). The two
     * deltas from a sale's own payload, exactly as Fourier specified: {@code transactionType} is
     * {@code "R"}, not {@code "S"}; {@code erpCodeRef} is the *original* sale's own {@code
     * erpCode} (its {@code invoiceNumber}), not {@code "0"}. {@code erpCode} itself is this
     * return's own {@link SalesReturn#getReturnNumber()} — a return needs its own identifier
     * distinct from the sale it's reversing, same as any other EFRIS transaction needs its own
     * erp_code. The line items are this return's own {@code SalesReturnLine}s (what was actually
     * returned, not the original sale's full quantities), each still reading its pricing/tax/
     * product details off the {@code OrderLine} it references, since a return never changes any
     * of that — only how many units are involved.
     */
    private EfrisInvoicePayload buildEfrisPayload(SalesReturn salesReturn) {
        SaleOrder order = salesReturn.getOrder();
        Customer customer = order != null ? order.getCustomer() : null;
        List<EfrisInvoiceLine> lines = salesReturn.getLines().stream()
                .map(returnLine -> {
                    OrderLine line = returnLine.getOrderLine();
                    return new EfrisInvoiceLine(
                            line != null ? line.getDescription() : null,
                            line != null ? orderService.resolveProductCode(line.getProduct()) : null,
                            returnLine.getQuantity(),
                            line != null ? line.getUnitPrice() : null,
                            line != null ? line.getTaxRatePercent() : null,
                            line != null && line.getTaxCode() != null && !line.getTaxCode().isBlank()
                                    ? line.getTaxCode() : DEFAULT_EFRIS_TAX_CODE,
                            line != null ? line.getUnspCode() : null,
                            line != null ? line.effectiveDiscountPercent() : null
                    );
                })
                .toList();
        return new EfrisInvoicePayload(
                "R",
                branchService.effectiveTaxId(salesReturn.getBranchId(), storeSettingsService.getSettings().taxId()),
                customer != null ? customer.getTaxId() : null,
                customer != null ? customer.getName() : null,
                customer != null ? customer.getPhone() : null,
                null,
                salesReturn.getReturnNumber(),
                order != null ? order.getInvoiceNumber() : "0",
                order != null ? orderService.resolvePaymentMode(order) : null,
                lines
        );
    }
}
