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
                           BranchService branchService) {
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

    @Transactional
    public SalesReturnDto processReturn(ProcessReturnRequest request) {
        SaleOrder order = orderService.getCompletedOrderById(request.getOrderId());
        String refundMethod = paymentMethodService.validateActiveCodeOrThrow(request.getRefundMethod());

        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setOrder(order);
        salesReturn.setRefundMethod(refundMethod);
        salesReturn.setReason(request.getReason());
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
                    line.getDiscountPercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
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

        SalesReturn saved = salesReturnRepository.save(salesReturn);
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
                            line != null ? line.getDiscountPercent() : null
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
