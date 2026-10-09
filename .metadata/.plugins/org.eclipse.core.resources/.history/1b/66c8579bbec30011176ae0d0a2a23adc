package com.posapp.inventory;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.bill.Bill;
import com.posapp.bill.BillRepository;
import com.posapp.bill.BillStatus;
import com.posapp.common.ApiException;
import com.posapp.common.TaxMath;
import com.posapp.email.DocumentPdfService;
import com.posapp.inventory.dto.OutstandingPurchaseOrderDto;
import com.posapp.inventory.dto.RecordBillPaymentRequest;
import com.posapp.inventory.dto.RecordSupplierPaymentRequest;
import com.posapp.inventory.dto.SupplierPaymentDto;
import com.posapp.paymentmethod.PaymentMethodService;
import com.posapp.settings.StoreSettingsService;
import com.posapp.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Add Supplier payment" — the accounts-payable mirror of {@code
 * com.posapp.credit.CustomerCreditService}: outstanding-balance math and recording payments
 * against a supplier's own purchase orders. Same reasoning throughout as the customer side (see
 * that class's javadoc), most importantly: there is deliberately no floating/unapplied supplier
 * balance concept — every payment's {@code allocations} must sum to exactly its {@code amount},
 * each applied to one specific PO up to that PO's own outstanding amount (see
 * {@link #recordPayment}).
 *
 * <p>A PO's {@code totalCost} isn't a stored column — it's computed the same way {@code
 * ReportService.purchaseOrderRow} does, from {@code sum(unitCost * quantityOrdered)} across its
 * lines — so {@link #totalCost} mirrors that exact calculation to stay consistent with what the
 * Purchase Order / Supplier Statement screens already show as "the" cost of an order.
 *
 * <p>What a PO "owes" only exists once it's actually been committed to the supplier: a DRAFT PO
 * hasn't been sent yet, so nothing is owed on it, and a CANCELLED one never will be — {@link
 * #amountOwedForOrder} returns zero for both, matching {@code CustomerCreditService
 * .outstandingForOrder}'s equivalent "not a real order yet" floor on the sales side.
 *
 * <p>Task 4: "Supplier Purchase Return (Cancellation after Confirmation and Payment) Inventory
 * Module" — {@link #amountOwedForOrder} now also subtracts whatever has been returned against a
 * PO (via {@link PurchaseReturnRepository}) before subtracting what's been paid, and {@link
 * #refundDueFromSupplierForOrder} surfaces the opposite case: a return that brings the net owed
 * amount below what's already been paid to the supplier. Informational only, mirroring {@code
 * CustomerCreditService.outstandingForSalesOrder}/{@code refundDueForSalesOrder} exactly.
 *
 * <p>Fourier: "The Purchase order should flow same workflow as Sale order (with Bills and Refunds
 * equivalent with Invoices and Credit note)" — same "the payment service owns the payable math,
 * the document service only reads it" split {@code CustomerCreditService} already draws for
 * {@code Invoice} (see that class's own javadoc): {@link #amountPaidForBill}/{@link
 * #outstandingForBill} live here, not on {@code com.posapp.bill.BillService}, which only calls
 * into them for its own DTOs. The instant a Purchase Order has a {@link Bill} of its own, {@link
 * #amountOwedForOrder}/{@link #amountPaidForOrder} redirect through it (exactly mirroring {@code
 * CustomerCreditService.outstandingForSalesOrder} folding in its own Invoice's payments) and
 * {@link #listOutstandingOrders} stops offering the Purchase Order directly as a payment target in
 * favor of the Bill (exactly mirroring {@code CustomerCreditService.listOutstandingOrders} no
 * longer offering a Sales Order once it has an Invoice) — see {@code PayableType}'s own javadoc.
 */
@Service
public class SupplierPaymentService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierPaymentAllocationRepository allocationRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final BillRepository billRepository;
    private final AuditLogService auditLogService;
    private final AccountingService accountingService;
    private final PaymentMethodService paymentMethodService;
    private final DocumentPdfService documentPdfService;
    private final StoreSettingsService storeSettingsService;
    /** Attributes a payment to the branch of the bills/orders it settles. */
    private final com.posapp.branch.BranchService branchService;

    public SupplierPaymentService(PurchaseOrderRepository purchaseOrderRepository,
                                   SupplierRepository supplierRepository,
                                   SupplierPaymentRepository supplierPaymentRepository,
                                   SupplierPaymentAllocationRepository allocationRepository,
                                   PurchaseReturnRepository purchaseReturnRepository,
                                   BillRepository billRepository,
                                   AuditLogService auditLogService,
                                   AccountingService accountingService,
                                   PaymentMethodService paymentMethodService,
                                   DocumentPdfService documentPdfService,
                                   StoreSettingsService storeSettingsService,
                                   com.posapp.branch.BranchService branchService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.supplierPaymentRepository = supplierPaymentRepository;
        this.allocationRepository = allocationRepository;
        this.purchaseReturnRepository = purchaseReturnRepository;
        this.billRepository = billRepository;
        this.auditLogService = auditLogService;
        this.accountingService = accountingService;
        this.paymentMethodService = paymentMethodService;
        this.documentPdfService = documentPdfService;
        this.storeSettingsService = storeSettingsService;
        this.branchService = branchService;
    }

    private static Long allocationBranchId(SupplierPaymentAllocation allocation) {
        if (allocation.getBill() != null) return allocation.getBill().getBranchId();
        if (allocation.getPurchaseOrder() != null) return allocation.getPurchaseOrder().getBranchId();
        return null;
    }

    private static BigDecimal lineGrossBeforeDiscount(PurchaseOrderLine line) {
        return line.getUnitCost().multiply(BigDecimal.valueOf(line.getQuantityOrdered())).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fourier: "Except POS other module can have global discount ... Either on line or global"
     * — the line's gross after the effective discountPercent (PER_LINE or GLOBAL, whichever
     * applies — see TaxMath.effectiveDiscountPercent) is knocked off; this is what tax is
     * actually computed on, same "discount before tax" ordering OrderCalculator.lineTotal
     * already uses for POS.
     */
    private static BigDecimal lineGross(BigDecimal headerDiscountPercent, PurchaseOrderLine line) {
        BigDecimal discountPercent = TaxMath.effectiveDiscountPercent(headerDiscountPercent, line.getDiscountPercent());
        return TaxMath.applyDiscount(lineGrossBeforeDiscount(line), discountPercent);
    }

    private static BigDecimal lineDiscountAmount(BigDecimal headerDiscountPercent, PurchaseOrderLine line) {
        return lineGrossBeforeDiscount(line).subtract(lineGross(headerDiscountPercent, line)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal lineTax(BigDecimal headerDiscountPercent, PurchaseOrderLine line) {
        return TaxMath.taxOnAmount(lineGross(headerDiscountPercent, line), line.getTaxRatePercent(), Boolean.TRUE.equals(line.getTaxInclusive()));
    }

    private static BigDecimal lineNet(BigDecimal headerDiscountPercent, PurchaseOrderLine line) {
        BigDecimal gross = lineGross(headerDiscountPercent, line);
        return TaxMath.netOfTax(gross, lineTax(headerDiscountPercent, line), Boolean.TRUE.equals(line.getTaxInclusive()));
    }

    /** Sum of every line's pre-tax (net) contribution — same shape as {@link #taxCost}/{@link #totalCost}. */
    public static BigDecimal netCost(PurchaseOrder po) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderLine line : po.getLines()) {
            total = total.add(lineNet(po.getDiscountPercent(), line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Sum of every line's own tax — zero when no line has a tax rate assigned (see PurchaseOrderLine.taxRatePercent's javadoc). */
    public static BigDecimal taxCost(PurchaseOrder po) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderLine line : po.getLines()) {
            total = total.add(lineTax(po.getDiscountPercent(), line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Fourier: "Same for discount ... it will be per line" — sum of every line's own discount, purely informational (already folded into netCost/totalCost above, via lineGross). Zero whenever the feature is off or no line has one. */
    public static BigDecimal discountCost(PurchaseOrder po) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderLine line : po.getLines()) {
            total = total.add(lineDiscountAmount(po.getDiscountPercent(), line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
     * {@link #netCost} + {@link #taxCost} (previously just {@code sum(unitCost *
     * quantityOrdered)}, with no tax at all). See class javadoc for why this isn't a stored
     * column.
     */
    public static BigDecimal totalCost(PurchaseOrder po) {
        return netCost(po).add(taxCost(po)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The canonical "how much of this PO is still owed" calculation, reused by the allocation
     * screen, the Supplier Statement and the Outstanding Payables report. Zero for DRAFT/CANCELLED
     * orders (see class javadoc); floored at zero otherwise for safety, same as the customer side.
     *
     * <p>Fourier: "same workflow as Sale order" — the instant this order has its own {@link Bill}
     * (any status), this redirects to {@link #outstandingForBill} instead of computing from the
     * order's own allocations directly — exactly mirroring {@code
     * CustomerCreditService.outstandingForSalesOrder} folding in its Invoice's own payments. A
     * purchase order never gets a second bill (see {@code Bill}'s class javadoc), so this is never
     * ambiguous.
     */
    @Transactional(readOnly = true)
    public BigDecimal amountOwedForOrder(PurchaseOrder po) {
        if (po.getStatus() == PurchaseOrderStatus.DRAFT || po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            return BigDecimal.ZERO;
        }
        Bill bill = billRepository.findByPurchaseOrderId(po.getId()).orElse(null);
        if (bill != null) {
            return outstandingForBill(bill);
        }
        BigDecimal applied = allocationRepository.sumAppliedForPurchaseOrder(po.getId());
        BigDecimal returned = returnedAmountForOrder(po);
        BigDecimal outstanding = totalCost(po).subtract(returned).subtract(applied);
        return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
    }

    /** How much has ever been returned against one Purchase Order, across every return visit — informational, see {@code PurchaseReturn}'s class javadoc. */
    @Transactional(readOnly = true)
    public BigDecimal returnedAmountForOrder(PurchaseOrder po) {
        return purchaseReturnRepository.sumRefundTotalForPurchaseOrder(po.getId());
    }

    /**
     * Task 4's "credit/refund owed back" figure, from the supplier's side: when a return brings
     * what's still owed (PO total minus returns) below what's already been paid to the supplier,
     * the difference is what the supplier now owes the store back — informational only, the same
     * "a figure to show, not a payout the system processes" choice as {@code
     * CustomerCreditService.refundDueForSalesOrder}.
     */
    @Transactional(readOnly = true)
    public BigDecimal refundDueFromSupplierForOrder(PurchaseOrder po) {
        // Fourier: "same workflow as Sale order" — amountPaidForOrder already redirects through
        // this order's own Bill once one exists, same reasoning as amountOwedForOrder above.
        BigDecimal applied = amountPaidForOrder(po);
        BigDecimal netOwed = totalCost(po).subtract(returnedAmountForOrder(po));
        BigDecimal refundDue = applied.subtract(netOwed);
        return refundDue.compareTo(BigDecimal.ZERO) > 0 ? refundDue : BigDecimal.ZERO;
    }

    /**
     * How much has actually been paid toward one PO so far, regardless of its status — unlike
     * {@link #amountOwedForOrder} this is never floored to zero for a DRAFT/CANCELLED PO, since a
     * payment could in principle have been recorded before a (since-cancelled) PO was cancelled.
     * Backs the "paid"/"outstanding" columns Task 5 added to Purchase Orders/Supplier Statement.
     *
     * <p>Redirects through this order's own {@link Bill} once one exists, same reasoning as
     * {@link #amountOwedForOrder}.
     */
    @Transactional(readOnly = true)
    public BigDecimal amountPaidForOrder(PurchaseOrder po) {
        Bill bill = billRepository.findByPurchaseOrderId(po.getId()).orElse(null);
        if (bill != null) {
            return amountPaidForBill(bill);
        }
        return allocationRepository.sumAppliedForPurchaseOrder(po.getId());
    }

    /**
     * Fourier: "same workflow as Sale order" — the Bill mirror of {@link #amountOwedForOrder},
     * and this module's own equivalent of {@code CustomerCreditService.outstandingForInvoice}.
     * Zero for anything but an {@code ISSUED} bill (a DRAFT isn't a real document yet, and a VOID
     * one is no longer valid — see {@code BillStatus}'s javadoc); also subtracts whatever's been
     * returned against the underlying Purchase Order (a return doesn't rewrite the bill's own
     * frozen {@code totalAmount}, so it has to be subtracted here instead, same as {@code
     * outstandingForInvoice} does for its Sales Order's own returns).
     */
    @Transactional(readOnly = true)
    public BigDecimal outstandingForBill(Bill bill) {
        if (bill.getStatus() != BillStatus.ISSUED) {
            return BigDecimal.ZERO;
        }
        BigDecimal returned = purchaseReturnRepository.sumRefundTotalForPurchaseOrder(bill.getPurchaseOrderId());
        BigDecimal outstanding = bill.getTotalAmount().subtract(returned).subtract(amountPaidForBill(bill));
        return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
    }

    /** How much has actually been paid toward one Bill so far — not status-gated (unlike {@link #outstandingForBill}), same reasoning as {@code CustomerCreditService.amountPaidForInvoice}. */
    @Transactional(readOnly = true)
    public BigDecimal amountPaidForBill(Bill bill) {
        return allocationRepository.sumAppliedForBill(bill.getId());
    }

    /** Sum of {@link #amountOwedForOrder} across every purchase order ever placed with this supplier. */
    @Transactional(readOnly = true)
    public BigDecimal outstandingBalance(Long supplierId) {
        return purchaseOrderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(po -> po.getSupplier() != null && po.getSupplier().getId().equals(supplierId))
                .map(this::amountOwedForOrder)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Task: "Add Supplier payment" — the Record Supplier Payment screen's allocation table.
     *
     * <p>Fourier: "same workflow as Sale order" — once a purchase order has its own {@link Bill},
     * this offers the Bill instead of the order directly (same {@code payableType}/{@code
     * orderNumber} shape {@code OutstandingPurchaseOrderDto} now carries for exactly that reason),
     * exactly mirroring how {@code CustomerCreditService.listOutstandingOrders} stopped offering a
     * Sales Order directly once it has an Invoice. An order with no bill yet (e.g. {@code SENT}
     * with nothing received against it) is still offered directly, same as before.
     */
    @Transactional(readOnly = true)
    public List<OutstandingPurchaseOrderDto> listOutstandingOrders(Long supplierId) {
        getSupplierOrThrow(supplierId);
        List<OutstandingPurchaseOrderDto> rows = new ArrayList<>();
        purchaseOrderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(po -> po.getSupplier() != null && po.getSupplier().getId().equals(supplierId))
                .filter(po -> billRepository.findByPurchaseOrderId(po.getId()).isEmpty())
                .forEach(po -> rows.add(new OutstandingPurchaseOrderDto(
                        po.getId(), PayableType.PURCHASE_ORDER, po.getOrderNumber(), po.getOrderDate(), po.getExpectedDate(),
                        totalCost(po), amountOwedForOrder(po))));
        billRepository.findBySupplierIdOrderByBillDateAsc(supplierId)
                .forEach(bill -> rows.add(new OutstandingPurchaseOrderDto(
                        bill.getId(), PayableType.BILL, bill.getBillNumber(), bill.getBillDate(), null,
                        bill.getTotalAmount(), outstandingForBill(bill))));
        return rows.stream()
                .filter(dto -> dto.amountOutstanding().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> a.orderDate().compareTo(b.orderDate()))
                .toList();
    }

    /**
     * {@code allocations} must sum to exactly {@code amount} (see class javadoc); each allocation
     * must target one of this supplier's own purchase orders and may not exceed that order's own
     * outstanding amount — a running per-order total across the entries in *this* request guards
     * against the same PO being listed twice and double-spending its outstanding amount before
     * either write commits. Mirrors {@code CustomerCreditService.recordPayment} line for line.
     */
    @Transactional
    public SupplierPaymentDto recordPayment(Long supplierId, RecordSupplierPaymentRequest request, AppUser caller) {
        Supplier supplier = getSupplierOrThrow(supplierId);

        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (RecordSupplierPaymentRequest.AllocationEntry entry : request.getAllocations()) {
            allocatedTotal = allocatedTotal.add(entry.getAmount());
        }
        if (allocatedTotal.compareTo(request.getAmount()) != 0) {
            throw ApiException.badRequest("Allocations (" + allocatedTotal
                    + ") must add up to exactly the payment amount (" + request.getAmount() + ")");
        }

        SupplierPayment payment = new SupplierPayment();
        payment.setSupplier(supplier);
        payment.setAmount(request.getAmount());
        payment.setPaidAt(Instant.now());
        payment.setNote(request.getNote());
        payment.setReceiptNumber("SPMT-" + System.currentTimeMillis());
        payment.setRecordedBy(caller);

        Map<String, BigDecimal> allocatedSoFarThisRequest = new HashMap<>();
        for (RecordSupplierPaymentRequest.AllocationEntry entry : request.getAllocations()) {
            PayableType payableType = entry.getPayableType() != null ? entry.getPayableType() : PayableType.PURCHASE_ORDER;
            String key = payableType + ":" + entry.getPurchaseOrderId();
            BigDecimal alreadyThisRequest = allocatedSoFarThisRequest.getOrDefault(key, BigDecimal.ZERO);

            SupplierPaymentAllocation allocation = new SupplierPaymentAllocation();
            allocation.setPayment(payment);
            allocation.setAmountApplied(entry.getAmount());

            if (payableType == PayableType.BILL) {
                // Fourier: "same workflow as Sale order" — the Bill mirror of the PURCHASE_ORDER
                // branch below, same bounds-checking shape as CustomerCreditService.recordPayment's
                // own INVOICE branch.
                Bill bill = billRepository.findById(entry.getPurchaseOrderId())
                        .orElseThrow(() -> ApiException.notFound("Bill not found: " + entry.getPurchaseOrderId()));
                if (bill.getSupplierId() == null || !bill.getSupplierId().equals(supplierId)) {
                    throw ApiException.badRequest("Bill " + entry.getPurchaseOrderId() + " does not belong to this supplier");
                }
                BigDecimal outstanding = outstandingForBill(bill);
                if (entry.getAmount().add(alreadyThisRequest).compareTo(outstanding) > 0) {
                    throw ApiException.badRequest("Allocation of " + entry.getAmount() + " to bill "
                            + bill.getBillNumber() + " exceeds its outstanding amount of " + outstanding);
                }
                allocation.setBill(bill);
            } else {
                PurchaseOrder po = purchaseOrderRepository.findById(entry.getPurchaseOrderId())
                        .orElseThrow(() -> ApiException.notFound("Purchase order not found: " + entry.getPurchaseOrderId()));
                if (po.getSupplier() == null || !po.getSupplier().getId().equals(supplierId)) {
                    throw ApiException.badRequest("Purchase order " + entry.getPurchaseOrderId() + " does not belong to this supplier");
                }
                BigDecimal outstanding = amountOwedForOrder(po);
                if (entry.getAmount().add(alreadyThisRequest).compareTo(outstanding) > 0) {
                    throw ApiException.badRequest("Allocation of " + entry.getAmount() + " to purchase order "
                            + po.getOrderNumber() + " exceeds its outstanding amount of " + outstanding);
                }
                allocation.setPurchaseOrder(po);
            }
            allocatedSoFarThisRequest.merge(key, entry.getAmount(), BigDecimal::add);
            payment.getAllocations().add(allocation);
        }
        // Branch: the one branch all settled documents belong to; a payment spread across branches stays unattributed.
        payment.setBranchId(branchService.commonBranchId(payment.getAllocations().stream().map(SupplierPaymentService::allocationBranchId).toList()));

        SupplierPayment saved = supplierPaymentRepository.save(payment);
        auditLogService.record("SUPPLIER_PAYMENT_RECORDED",
                "Recorded payment of " + saved.getAmount() + " to " + supplier.getName()
                        + " (" + saved.getAllocations().size() + " allocation" + (saved.getAllocations().size() == 1 ? "" : "s") + ")",
                "SupplierPayment", saved.getId());

        // Fourier: "all...Inventory...module will be integrated when accounting is enabled" — see
        // AccountingService.postSupplierPayment's own javadoc.
        accountingService.postSupplierPayment(saved);

        return SupplierPaymentDto.from(saved, outstandingBalance(supplierId));
    }

    /** Store-wide payment history, most recent first — backs the new Supplier Payments list screen. */
    @Transactional(readOnly = true)
    public List<SupplierPaymentDto> paymentHistory() {
        return supplierPaymentRepository.findAllByOrderByPaidAtDesc().stream()
                .map(p -> SupplierPaymentDto.from(p, outstandingBalance(p.getSupplier().getId())))
                .toList();
    }

    /** One supplier's own payment history, most recent first. */
    @Transactional(readOnly = true)
    public List<SupplierPaymentDto> paymentHistory(Long supplierId) {
        getSupplierOrThrow(supplierId);
        BigDecimal currentBalance = outstandingBalance(supplierId);
        return supplierPaymentRepository.findBySupplierIdOrderByPaidAtDesc(supplierId)
                .stream().map(p -> SupplierPaymentDto.from(p, currentBalance)).toList();
    }

    /**
     * Fourier: "same workflow as Sale order" — the single-bill counterpart of {@link
     * #recordPayment}, the accounts-payable mirror of {@code
     * CustomerCreditService#recordInvoicePayment}: always applies the whole amount to this one
     * bill, with no {@code allocations} list and no chance to spread it across any of the
     * supplier's other outstanding purchase orders or bills. This is what the Bill detail screen's
     * own "Record payment" button calls.
     */
    @Transactional
    public SupplierPaymentDto recordBillPayment(Long billId, RecordBillPaymentRequest request, AppUser caller) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> ApiException.notFound("Bill not found: " + billId));
        if (bill.getStatus() != BillStatus.ISSUED) {
            throw ApiException.badRequest("Only an issued bill can be paid — " + bill.getBillNumber() + " is " + bill.getStatus());
        }
        if (bill.getSupplierId() == null) {
            throw ApiException.badRequest("Bill " + bill.getBillNumber() + " has no supplier to record a payment against");
        }
        Supplier supplier = getSupplierOrThrow(bill.getSupplierId());

        BigDecimal outstanding = outstandingForBill(bill);
        if (request.getAmount().compareTo(outstanding) > 0) {
            throw ApiException.badRequest("Payment of " + request.getAmount() + " exceeds bill " + bill.getBillNumber()
                    + "'s outstanding amount of " + outstanding);
        }

        SupplierPayment payment = new SupplierPayment();
        payment.setSupplier(supplier);
        payment.setAmount(request.getAmount());
        payment.setPaidAt(Instant.now());
        // Fourier: "Add payment method when recording bill payment" — same active-code validation
        // RecordInvoicePaymentRequest.method already goes through on the customer side.
        payment.setMethod(paymentMethodService.validateActiveCodeOrThrow(request.getMethod()));
        payment.setNote(request.getNote());
        payment.setReceiptNumber("SPMT-" + System.currentTimeMillis());
        payment.setRecordedBy(caller);

        SupplierPaymentAllocation allocation = new SupplierPaymentAllocation();
        allocation.setPayment(payment);
        allocation.setBill(bill);
        allocation.setAmountApplied(request.getAmount());
        payment.getAllocations().add(allocation);
        payment.setBranchId(bill.getBranchId());

        SupplierPayment saved = supplierPaymentRepository.save(payment);
        auditLogService.record("SUPPLIER_PAYMENT_RECORDED",
                "Recorded payment of " + saved.getAmount() + " to " + supplier.getName() + " against bill " + bill.getBillNumber(),
                "SupplierPayment", saved.getId());

        // Fourier: "all...Inventory...module will be integrated when accounting is enabled" — same
        // posting as any other supplier payment, see AccountingService.postSupplierPayment's own
        // javadoc — the Accounts Payable liability doesn't care whether it's tracked at the PO or
        // the Bill level.
        accountingService.postSupplierPayment(saved);

        return SupplierPaymentDto.from(saved, outstandingBalance(supplier.getId()));
    }

    /** One bill's own payment history — see {@link #recordBillPayment}'s javadoc. */
    @Transactional(readOnly = true)
    public List<SupplierPaymentDto> paymentHistoryForBill(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> ApiException.notFound("Bill not found: " + billId));
        BigDecimal currentBalance = bill.getSupplierId() != null ? outstandingBalance(bill.getSupplierId()) : BigDecimal.ZERO;
        return allocationRepository.findByBillId(billId).stream()
                .map(SupplierPaymentAllocation::getPayment)
                .distinct()
                .sorted((a, b) -> b.getPaidAt().compareTo(a.getPaidAt()))
                .map(p -> SupplierPaymentDto.from(p, currentBalance))
                .toList();
    }

    /**
     * Fourier: "Add view details on supplier payments list" — looks up one payment by its own id
     * regardless of which supplier it belongs to, the accounts-payable mirror of {@code
     * CustomerCreditService.getPaymentReceipt}. {@code supplierBalanceAfter} is always the
     * supplier's *current* outstanding balance, same "not frozen at payment time" reasoning as
     * {@code SupplierPaymentDto}'s own class javadoc.
     */
    @Transactional(readOnly = true)
    public SupplierPaymentDto getPaymentReceipt(Long paymentId) {
        SupplierPayment payment = supplierPaymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found: " + paymentId));
        BigDecimal currentBalance = outstandingBalance(payment.getSupplier().getId());
        return SupplierPaymentDto.from(payment, currentBalance);
    }

    /**
     * Fourier: "Add view details on supplier payments list" — an A4 PDF of this one payment to
     * open in a new tab and print, the accounts-payable mirror of {@code CustomerCreditService
     * .pdf}.
     */
    @Transactional(readOnly = true)
    public byte[] pdf(Long paymentId) {
        return documentPdfService.supplierPaymentReceiptPdf(getPaymentReceipt(paymentId), storeSettingsService.getSettingsEntity());
    }

    private Supplier getSupplierOrThrow(Long supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> ApiException.notFound("Supplier not found: " + supplierId));
    }
}
