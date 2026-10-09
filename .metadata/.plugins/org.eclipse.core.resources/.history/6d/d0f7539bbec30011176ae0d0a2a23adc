package com.posapp.credit;

import com.posapp.accounting.AccountingService;
import com.posapp.alerts.AlertMailService;
import com.posapp.common.ApiException;
import com.posapp.credit.dto.CreditSummaryDto;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.credit.dto.OutstandingOrderDto;
import com.posapp.credit.dto.RecordInvoicePaymentRequest;
import com.posapp.credit.dto.RecordPaymentRequest;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.DocumentPdfService;
import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceStatus;
import com.posapp.order.OrderRepository;
import com.posapp.order.OrderStatus;
import com.posapp.order.SaleOrder;
import com.posapp.salesorder.SalesOrder;
import com.posapp.salesorder.SalesOrderRepository;
import com.posapp.salesorder.SalesOrderReturnRepository;
import com.posapp.salesorder.SalesOrderService;
import com.posapp.salesorder.SalesOrderStatus;
import com.posapp.settings.StoreSettingsService;
import com.posapp.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole store-credit/AR subsystem in one place: outstanding-balance math, checkout-time limit
 * enforcement, and recording customer payments against their outstanding orders.
 *
 * <p>Deliberately depends on {@link OrderRepository}/{@link CustomerRepository} directly rather
 * than {@code OrderService}, mirroring how {@code ReportService} already reads orders directly
 * for the same reason: {@code OrderService.checkout} needs this service (to enforce the credit
 * limit and stamp {@code creditAmount}/{@code dueDate}), so this service depending on
 * {@code OrderService} back would be a circular Spring bean dependency. This service reads Sales
 * Orders the same way, via {@link SalesOrderRepository} directly rather than {@code
 * SalesOrderService} — not to avoid a cycle (Task: "the rules of enabling credit on customer
 * should only be required in POS Module not in Sale Module" — {@code SalesOrderService.confirm}
 * no longer calls into this service at all, see its own javadoc for why), but because computing a
 * Sales Order's outstanding/paid amount for reporting and payment allocation is all this service
 * ever needs from one. It reaches {@link SalesOrderService#totalAmount} as a plain static helper
 * rather than an injected bean for the same "only needs the one calculation" reason.
 *
 * <p>There is deliberately no floating/unapplied customer credit balance concept — every payment
 * recorded here must have its {@code allocations} sum to exactly its {@code amount}, each applied
 * to a specific outstanding order up to that order's own outstanding amount (see
 * {@link #recordPayment}). This is simpler and stricter than a running-balance sub-ledger, and
 * wasn't asked for.
 *
 * <p>Task: "Add Sale order and Customer payment... so it shows up alongside register sales in one
 * place" — every method below now merges POS credit sales ({@link SaleOrder}) with
 * confirmed/partially-fulfilled/fulfilled back-office Sales Orders ({@link SalesOrder}), so a
 * customer's outstanding balance, their allocation-table rows, and every credit report all show
 * both kinds of order without the caller needing to know the difference. Only {@link
 * SalesOrderStatus#CONFIRMED}/{@code PARTIALLY_FULFILLED}/{@code FULFILLED} Sales Orders carry an
 * amount owed — a DRAFT hasn't committed the customer to anything yet, and a CANCELLED one never
 * will — the exact mirror of {@code SupplierPaymentService.amountOwedForOrder}'s equivalent floor
 * on the purchasing side.
 *
 * <p>Task 3: "Sale Order Return (Cancellation after Confirmation and Payment) Sale Module" —
 * {@link #outstandingForSalesOrder} now also subtracts whatever has been returned against a Sales
 * Order (via {@code SalesOrderReturnRepository}) before subtracting what's been paid, and {@link
 * #refundDueForSalesOrder} surfaces the opposite case: a return that brings the net owed amount
 * below what the customer already paid. Per Fourier's own choice, this is purely informational —
 * nothing here moves money, it only changes what these two figures report.
 *
 * <p>Fourier's later request: "create invoice related to the Sale Order and record payment on
 * invoice ... someone can create invoice (with invoice lines) without creating sales order."
 * {@link #listOutstandingOrders}/{@link #outstandingBalance} no longer offer a back-office Sales
 * Order directly as a *new* payment target — once one exists, its {@code Invoice} is, via {@link
 * #outstandingForInvoice}/{@link #amountPaidForInvoice}. {@link #outstandingForSalesOrder}/{@link
 * #amountPaidForSalesOrder} still fold in whatever's been applied to that order's own invoice (so
 * the Sales Order detail screen's own paid/outstanding figures stay accurate either way), *and*
 * whatever was recorded directly against the order before this change (the {@code salesOrder} arc
 * on {@code CustomerPaymentAllocation}, now legacy-only) — so nothing written under the old model
 * needs a data migration to keep reading correctly under the new one.
 */
@Service
public class CustomerCreditService {

    private static final List<SalesOrderStatus> OWED_SALES_ORDER_STATUSES =
            List.of(SalesOrderStatus.CONFIRMED, SalesOrderStatus.PARTIALLY_FULFILLED, SalesOrderStatus.FULFILLED);

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerPaymentRepository customerPaymentRepository;
    private final CustomerPaymentAllocationRepository allocationRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderReturnRepository salesOrderReturnRepository;
    /** Read directly, same "no circular Spring dependency" reasoning as {@code salesOrderRepository} above — see class javadoc. */
    private final InvoiceRepository invoiceRepository;
    private final com.posapp.paymentmethod.PaymentMethodService paymentMethodService;
    private final AccountingService accountingService;
    private final AlertMailService alertMailService;
    /** Fourier: "Change payment receipt printout. Make a PDF with A4" — see {@link #pdf}. */
    private final DocumentPdfService documentPdfService;
    private final StoreSettingsService storeSettingsService;
    /** Attributes a payment to the branch of the documents it settles. */
    private final com.posapp.branch.BranchService branchService;
    /** Fourier: "Wiring a handful of business events to that same mailer" — see {@link #assertWithinLimit}. */
    private final int creditLimitWarningPercent;

    public CustomerCreditService(OrderRepository orderRepository,
                                  CustomerRepository customerRepository,
                                  CustomerPaymentRepository customerPaymentRepository,
                                  CustomerPaymentAllocationRepository allocationRepository,
                                  SalesOrderRepository salesOrderRepository,
                                  SalesOrderReturnRepository salesOrderReturnRepository,
                                  InvoiceRepository invoiceRepository,
                                  com.posapp.paymentmethod.PaymentMethodService paymentMethodService,
                                  AccountingService accountingService,
                                  AlertMailService alertMailService,
                                  DocumentPdfService documentPdfService,
                                  StoreSettingsService storeSettingsService,
                                  com.posapp.branch.BranchService branchService,
                                  @Value("${app.pos.credit-limit-warning-percent:90}") int creditLimitWarningPercent) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.customerPaymentRepository = customerPaymentRepository;
        this.allocationRepository = allocationRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderReturnRepository = salesOrderReturnRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentMethodService = paymentMethodService;
        this.accountingService = accountingService;
        this.alertMailService = alertMailService;
        this.documentPdfService = documentPdfService;
        this.storeSettingsService = storeSettingsService;
        this.branchService = branchService;
        this.creditLimitWarningPercent = creditLimitWarningPercent;
    }

    /**
     * The canonical "how much of this order is still owed" calculation — reused everywhere
     * (checkout limit enforcement, the allocation-suggestion screen, every credit report) so
     * there's a single source of truth. Floored at zero: allocations can never exceed
     * {@code creditAmount} (enforced in {@link #recordPayment}), but floor anyway for safety.
     */
    @Transactional(readOnly = true)
    public BigDecimal outstandingForOrder(SaleOrder order) {
        if (order.getCreditAmount() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal applied = allocationRepository.sumAppliedForOrder(order.getId());
        BigDecimal outstanding = order.getCreditAmount().subtract(applied);
        return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
    }

    /**
     * The Sales Order mirror of {@link #outstandingForOrder} — zero for a DRAFT/CANCELLED order
     * (see class javadoc), floored at zero otherwise for the same reason. Task 3: also subtracts
     * whatever has been returned against this order (see {@link #returnedAmountForSalesOrder})
     * before subtracting what's been paid, so a return immediately reduces what's still owed —
     * the opposite case, where a return leaves the customer having overpaid, is {@link
     * #refundDueForSalesOrder}, not a negative number here.
     */
    @Transactional(readOnly = true)
    public BigDecimal outstandingForSalesOrder(SalesOrder salesOrder) {
        if (!OWED_SALES_ORDER_STATUSES.contains(salesOrder.getStatus())) {
            return BigDecimal.ZERO;
        }
        BigDecimal applied = totalAppliedForSalesOrder(salesOrder);
        BigDecimal returned = returnedAmountForSalesOrder(salesOrder);
        BigDecimal outstanding = SalesOrderService.totalAmount(salesOrder).subtract(returned).subtract(applied);
        return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
    }

    /** How much this customer has actually paid toward one Sales Order so far — the sales-order counterpart of {@code SupplierPaymentService.amountPaidForOrder}. */
    @Transactional(readOnly = true)
    public BigDecimal amountPaidForSalesOrder(SalesOrder salesOrder) {
        return totalAppliedForSalesOrder(salesOrder);
    }

    /**
     * Everything ever applied toward one Sales Order, whichever arc it was recorded under —
     * legacy direct payments against the order itself (the {@code salesOrder} arc, from before
     * Fourier's "record payment on invoice" request) plus whatever's been applied to that order's
     * own {@code Invoice}, if one has ever been generated for it (the {@code invoice} arc — see
     * {@code CustomerPaymentAllocation}'s "exclusive arc" javadoc). Deliberately sums *both* arcs
     * rather than switching between them by invoice status: voiding an invoice "never reverses
     * anything" (see {@code InvoiceService.voidInvoice}'s javadoc), so a payment already applied
     * to it must keep counting here even after it's voided.
     */
    private BigDecimal totalAppliedForSalesOrder(SalesOrder salesOrder) {
        BigDecimal legacy = allocationRepository.sumAppliedForSalesOrder(salesOrder.getId());
        Invoice invoice = invoiceRepository.findBySalesOrderId(salesOrder.getId()).orElse(null);
        BigDecimal viaInvoice = invoice != null ? allocationRepository.sumAppliedForInvoice(invoice.getId()) : BigDecimal.ZERO;
        return legacy.add(viaInvoice);
    }

    /**
     * The Invoice mirror of {@link #outstandingForSalesOrder} — Fourier: "record payment on
     * invoice." Zero for anything but an {@code ISSUED} invoice (a DRAFT isn't a real document
     * yet, and a VOID one is no longer valid — see {@code InvoiceStatus}'s javadoc) — that's what
     * keeps a voided or still-draft invoice from being offered as a payment target by {@link
     * #listOutstandingOrders}/{@link #recordPayment}. When this invoice was generated from a
     * Sales Order, also subtracts whatever's been returned against that order (a return doesn't
     * rewrite the invoice's own frozen {@code totalAmount}, so it has to be subtracted here
     * instead) and whatever was paid directly against the order under the old, pre-invoice model
     * (the {@code salesOrder} arc) — the same "both arcs count" reasoning as {@link
     * #totalAppliedForSalesOrder}, just entered from the invoice side instead of the order side.
     */
    @Transactional(readOnly = true)
    public BigDecimal outstandingForInvoice(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            return BigDecimal.ZERO;
        }
        BigDecimal returned = invoice.getSalesOrderId() != null
                ? salesOrderReturnRepository.sumRefundTotalForSalesOrder(invoice.getSalesOrderId())
                : BigDecimal.ZERO;
        BigDecimal outstanding = invoice.getTotalAmount().subtract(returned).subtract(amountPaidForInvoice(invoice));
        return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
    }

    /** How much has actually been paid toward one Invoice so far, across both arcs — see {@link #outstandingForInvoice}'s javadoc. Not status-gated (unlike that method) — a voided invoice's own already-recorded payments still count as paid, they just stop being payable any further. */
    @Transactional(readOnly = true)
    public BigDecimal amountPaidForInvoice(Invoice invoice) {
        BigDecimal legacy = invoice.getSalesOrderId() != null
                ? allocationRepository.sumAppliedForSalesOrder(invoice.getSalesOrderId())
                : BigDecimal.ZERO;
        BigDecimal viaInvoice = allocationRepository.sumAppliedForInvoice(invoice.getId());
        return legacy.add(viaInvoice);
    }

    /** How much has ever been returned against one Sales Order, across every return visit — informational, see {@code SalesOrderReturn}'s class javadoc. */
    @Transactional(readOnly = true)
    public BigDecimal returnedAmountForSalesOrder(SalesOrder salesOrder) {
        return salesOrderReturnRepository.sumRefundTotalForSalesOrder(salesOrder.getId());
    }

    /**
     * Task 3's "credit/refund owed back" figure: when a return brings what's still owed (order
     * total minus returns) below what the customer already paid, the difference is what the store
     * now owes the customer back — informational only, since Fourier's chosen model tracks this
     * as a figure to show, not a payout the system processes. Zero whenever nothing has been
     * overpaid relative to the net (post-return) amount owed.
     */
    @Transactional(readOnly = true)
    public BigDecimal refundDueForSalesOrder(SalesOrder salesOrder) {
        BigDecimal applied = totalAppliedForSalesOrder(salesOrder);
        BigDecimal netOwed = SalesOrderService.totalAmount(salesOrder).subtract(returnedAmountForSalesOrder(salesOrder));
        BigDecimal refundDue = applied.subtract(netOwed);
        return refundDue.compareTo(BigDecimal.ZERO) > 0 ? refundDue : BigDecimal.ZERO;
    }

    /**
     * Sum of {@link #outstandingForOrder}/{@link #outstandingForSalesOrder} across every credit
     * sale and Sales Order this customer has ever made, plus {@link #outstandingForInvoice}
     * across every *standalone* invoice (Fourier: "someone can create invoice ... without
     * creating sales order") — a Sales-Order-derived invoice is deliberately not summed again
     * here, since {@link #outstandingForSalesOrder} already folds its payments in (see that
     * method's own javadoc); only summing standalone invoices here avoids double-counting those.
     */
    @Transactional(readOnly = true)
    public BigDecimal outstandingBalance(Long customerId) {
        BigDecimal posOutstanding = orderRepository.findCreditOrdersForCustomer(customerId, OrderStatus.COMPLETED)
                .stream()
                .map(this::outstandingForOrder)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salesOrderOutstanding = salesOrderRepository
                .findByCustomerIdAndStatusInOrderByOrderDateAsc(customerId, OWED_SALES_ORDER_STATUSES)
                .stream()
                .map(this::outstandingForSalesOrder)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal standaloneInvoiceOutstanding = invoiceRepository.findByCustomerIdOrderByInvoiceDateAsc(customerId)
                .stream()
                .filter(inv -> inv.getSalesOrderId() == null)
                .map(this::outstandingForInvoice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return posOutstanding.add(salesOrderOutstanding).add(standaloneInvoiceOutstanding);
    }

    /**
     * Task: "Credit limit enforcement" — called from {@code OrderService.checkout} before a
     * credit-flagged payment is accepted. A no-op when this sale has no credit portion at all.
     * <p>
     * Fourier: "Wiring a handful of business events to that same mailer" — once a sale clears the
     * hard limit check above, this also fires the "nearing credit limit" alert the moment the
     * projected outstanding balance crosses from below {@code creditLimitWarningPercent}% of the
     * limit to at/above it (before &lt; warning amount, after &gt;= warning amount). Checking the
     * crossing rather than just "is currently above the warning line" means a customer sitting
     * just inside the warning zone across several ordinary sales only alerts once, the same
     * crossing-not-level shape {@code CatalogService.adjustStock}'s low-stock alert already uses.
     */
    @Transactional(readOnly = true)
    public void assertWithinLimit(Customer customer, BigDecimal creditPortion) {
        if (creditPortion == null || creditPortion.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (!customer.isCreditEnabled()) {
            throw ApiException.badRequest("Customer '" + customer.getName() + "' is not enabled for credit purchases");
        }
        BigDecimal currentOutstanding = outstandingBalance(customer.getId());
        BigDecimal projected = currentOutstanding.add(creditPortion);
        if (projected.compareTo(customer.getCreditLimit()) > 0) {
            BigDecimal available = customer.getCreditLimit().subtract(currentOutstanding);
            if (available.compareTo(BigDecimal.ZERO) < 0) {
                available = BigDecimal.ZERO;
            }
            throw ApiException.badRequest("This sale would exceed " + customer.getName()
                    + "'s credit limit — available credit is " + available + ", this sale needs " + creditPortion);
        }
        BigDecimal warningAmount = customer.getCreditLimit()
                .multiply(BigDecimal.valueOf(creditLimitWarningPercent))
                .divide(BigDecimal.valueOf(100));
        if (currentOutstanding.compareTo(warningAmount) < 0 && projected.compareTo(warningAmount) >= 0) {
            alertMailService.alertCustomerNearingCreditLimit(customer, projected);
        }
    }

    /** Task: "Customer balance on POS screen" — open to any logged-in user; see CreditSummaryDto's javadoc. */
    @Transactional(readOnly = true)
    public CreditSummaryDto creditSummary(Long customerId) {
        Customer customer = getCustomerOrThrow(customerId);
        BigDecimal outstanding = outstandingBalance(customerId);
        BigDecimal available;
        if (!customer.isCreditEnabled()) {
            available = BigDecimal.ZERO;
        } else {
            available = customer.getCreditLimit().subtract(outstanding);
            if (available.compareTo(BigDecimal.ZERO) < 0) {
                available = BigDecimal.ZERO;
            }
        }
        return new CreditSummaryDto(
                customer.getId(),
                customer.isCreditEnabled(),
                customer.getCreditLimit(),
                outstanding,
                available,
                customer.getDefaultCreditTermDays()
        );
    }

    /**
     * Task: "Payment allocation to orders" — the Record Payment screen's allocation table,
     * oldest-first. ADMIN-only (see controller). Merges POS credit sales and Invoices into one
     * list, sorted oldest-first by the same date each row uses elsewhere ({@code completedAt} for
     * a POS sale, {@code invoiceDate} for an Invoice) — see class javadoc.
     *
     * <p>Fourier: "create invoice related to the Sale Order and record payment on invoice" — a
     * back-office Sales Order is deliberately never offered directly here any more, however much
     * it owes: once confirmed, generate its Invoice (one click on the order's own detail screen)
     * and that Invoice shows up here instead, the same as a standalone one would. This is what
     * actually moves "record payment" from the Sales Order onto its Invoice.
     */
    @Transactional(readOnly = true)
    public List<OutstandingOrderDto> listOutstandingOrders(Long customerId) {
        getCustomerOrThrow(customerId);
        List<OutstandingOrderDto> rows = new ArrayList<>();
        orderRepository.findCreditOrdersForCustomer(customerId, OrderStatus.COMPLETED)
                .forEach(o -> rows.add(new OutstandingOrderDto(
                        o.getId(), com.posapp.credit.OrderType.POS_SALE, o.getInvoiceNumber(), o.getCompletedAt(), o.getDueDate(),
                        o.getCreditAmount(), outstandingForOrder(o))));
        invoiceRepository.findByCustomerIdOrderByInvoiceDateAsc(customerId)
                .forEach(inv -> rows.add(new OutstandingOrderDto(
                        inv.getId(), com.posapp.credit.OrderType.INVOICE, inv.getInvoiceNumber(), inv.getInvoiceDate(), inv.getDueDate(),
                        inv.getTotalAmount(), outstandingForInvoice(inv))));
        return rows.stream()
                .filter(dto -> dto.amountOutstanding().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> a.completedAt().compareTo(b.completedAt()))
                .toList();
    }

    /**
     * Task family: "Customer payment later" / "Partial settlement" / "Multiple payments against
     * one order" / "Payment allocation to orders" / "Payment receipt". {@code allocations} must
     * sum to exactly {@code amount} (see class javadoc); each allocation must target one of this
     * customer's own credit orders and may not exceed that order's own outstanding amount — a
     * running per-order total across the entries in *this* request guards against the same order
     * being listed twice and double-spending its outstanding amount before either write commits.
     */
    private static Long allocationBranchId(CustomerPaymentAllocation allocation) {
        if (allocation.getInvoice() != null) return allocation.getInvoice().getBranchId();
        if (allocation.getSalesOrder() != null) return allocation.getSalesOrder().getBranchId();
        if (allocation.getOrder() != null) return allocation.getOrder().getBranchId();
        return null;
    }

    @Transactional
    public CustomerPaymentDto recordPayment(Long customerId, RecordPaymentRequest request, AppUser caller) {
        Customer customer = getCustomerOrThrow(customerId);

        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (RecordPaymentRequest.AllocationEntry entry : request.getAllocations()) {
            allocatedTotal = allocatedTotal.add(entry.getAmount());
        }
        if (allocatedTotal.compareTo(request.getAmount()) != 0) {
            throw ApiException.badRequest("Allocations (" + allocatedTotal
                    + ") must add up to exactly the payment amount (" + request.getAmount() + ")");
        }

        CustomerPayment payment = new CustomerPayment();
        payment.setCustomer(customer);
        payment.setAmount(request.getAmount());
        payment.setPaidAt(Instant.now());
        // Task: "To record Payment in Sale Order add payment mode (CASH, CHEQUE, ETC)" — same
        // active-code validation a checkout/refund tender goes through.
        payment.setMethod(paymentMethodService.validateActiveCodeOrThrow(request.getMethod()));
        payment.setNote(request.getNote());
        payment.setReceiptNumber("PMT-" + System.currentTimeMillis());
        payment.setRecordedBy(caller);

        // Keyed by "POS_SALE:<id>"/"SALES_ORDER:<id>" rather than bare order id — a POS SaleOrder
        // and a SalesOrder can coincidentally share the same numeric id (unrelated tables, both
        // starting their own IDENTITY sequence from 1), so the order type has to be part of the key.
        Map<String, BigDecimal> allocatedSoFarThisRequest = new HashMap<>();
        for (RecordPaymentRequest.AllocationEntry entry : request.getAllocations()) {
            com.posapp.credit.OrderType orderType = entry.getOrderType() != null ? entry.getOrderType() : com.posapp.credit.OrderType.POS_SALE;
            String key = orderType + ":" + entry.getOrderId();
            BigDecimal alreadyThisRequest = allocatedSoFarThisRequest.getOrDefault(key, BigDecimal.ZERO);

            CustomerPaymentAllocation allocation = new CustomerPaymentAllocation();
            allocation.setPayment(payment);
            allocation.setAmountApplied(entry.getAmount());

            if (orderType == com.posapp.credit.OrderType.INVOICE) {
                Invoice invoice = invoiceRepository.findById(entry.getOrderId())
                        .orElseThrow(() -> ApiException.notFound("Invoice not found: " + entry.getOrderId()));
                if (invoice.getCustomerId() == null || !invoice.getCustomerId().equals(customerId)) {
                    throw ApiException.badRequest("Invoice " + entry.getOrderId() + " does not belong to this customer");
                }
                BigDecimal outstanding = outstandingForInvoice(invoice);
                if (entry.getAmount().add(alreadyThisRequest).compareTo(outstanding) > 0) {
                    throw ApiException.badRequest("Allocation of " + entry.getAmount() + " to invoice "
                            + invoice.getInvoiceNumber() + " exceeds its outstanding amount of " + outstanding);
                }
                allocation.setInvoice(invoice);
            } else if (orderType == com.posapp.credit.OrderType.SALES_ORDER) {
                // Legacy path — kept only so a payment recorded before Fourier's "record payment
                // on invoice" request still works if ever replayed; listOutstandingOrders no
                // longer surfaces a Sales Order as a new payment target (see its own javadoc).
                SalesOrder salesOrder = salesOrderRepository.findById(entry.getOrderId())
                        .orElseThrow(() -> ApiException.notFound("Sales order not found: " + entry.getOrderId()));
                if (salesOrder.getCustomer() == null || !salesOrder.getCustomer().getId().equals(customerId)) {
                    throw ApiException.badRequest("Sales order " + entry.getOrderId() + " does not belong to this customer");
                }
                BigDecimal outstanding = outstandingForSalesOrder(salesOrder);
                if (entry.getAmount().add(alreadyThisRequest).compareTo(outstanding) > 0) {
                    throw ApiException.badRequest("Allocation of " + entry.getAmount() + " to sales order "
                            + salesOrder.getOrderNumber() + " exceeds its outstanding amount of " + outstanding);
                }
                allocation.setSalesOrder(salesOrder);
            } else {
                SaleOrder order = orderRepository.findById(entry.getOrderId())
                        .orElseThrow(() -> ApiException.notFound("Order not found: " + entry.getOrderId()));
                if (order.getCustomer() == null || !order.getCustomer().getId().equals(customerId)) {
                    throw ApiException.badRequest("Order " + entry.getOrderId() + " does not belong to this customer");
                }
                BigDecimal outstanding = outstandingForOrder(order);
                if (entry.getAmount().add(alreadyThisRequest).compareTo(outstanding) > 0) {
                    throw ApiException.badRequest("Allocation of " + entry.getAmount() + " to order "
                            + order.getInvoiceNumber() + " exceeds its outstanding amount of " + outstanding);
                }
                allocation.setOrder(order);
            }
            allocatedSoFarThisRequest.merge(key, entry.getAmount(), BigDecimal::add);
            payment.getAllocations().add(allocation);
        }
        // Branch: the one branch all settled documents belong to; a payment spread across branches stays unattributed.
        payment.setBranchId(branchService.commonBranchId(payment.getAllocations().stream().map(CustomerCreditService::allocationBranchId).toList()));

        CustomerPayment saved = customerPaymentRepository.save(payment);

        // Fourier: "all POS, Sale...module will be integrated when accounting is enabled" — see
        // AccountingService.postCustomerPayment's own javadoc.
        accountingService.postCustomerPayment(saved);

        return CustomerPaymentDto.from(saved, outstandingBalance(customerId));
    }

    /**
     * Fourier: "Let record payment per invoice not per customer (all outstanding invoices)" — the
     * single-invoice counterpart of {@link #recordPayment} above: always applies the whole amount
     * to this one invoice, with no {@code allocations} list and no chance to spread it across any
     * of the customer's other outstanding invoices or orders. This is what the Invoice detail
     * screen's own "Record payment" button now calls (see {@code InvoiceService.recordPayment});
     * {@link #recordPayment} still exists underneath for the Customer-level screen, now scoped to
     * POS credit sales only (see {@code RecordPaymentComponent}'s own javadoc) and for the
     * Customer Payments/report screens reading what either path already wrote.
     */
    @Transactional
    public CustomerPaymentDto recordInvoicePayment(Long invoiceId, RecordInvoicePaymentRequest request, AppUser caller) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found: " + invoiceId));
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw ApiException.badRequest("Only an issued invoice can be paid — " + invoice.getInvoiceNumber() + " is " + invoice.getStatus());
        }
        if (invoice.getCustomerId() == null) {
            throw ApiException.badRequest("Invoice " + invoice.getInvoiceNumber() + " has no customer to record a payment against");
        }
        Customer customer = getCustomerOrThrow(invoice.getCustomerId());

        BigDecimal outstanding = outstandingForInvoice(invoice);
        if (request.getAmount().compareTo(outstanding) > 0) {
            throw ApiException.badRequest("Payment of " + request.getAmount() + " exceeds invoice " + invoice.getInvoiceNumber()
                    + "'s outstanding amount of " + outstanding);
        }

        CustomerPayment payment = new CustomerPayment();
        payment.setCustomer(customer);
        payment.setAmount(request.getAmount());
        payment.setPaidAt(Instant.now());
        payment.setMethod(paymentMethodService.validateActiveCodeOrThrow(request.getMethod()));
        payment.setNote(request.getNote());
        payment.setReceiptNumber("PMT-" + System.currentTimeMillis());
        payment.setRecordedBy(caller);

        CustomerPaymentAllocation allocation = new CustomerPaymentAllocation();
        allocation.setPayment(payment);
        allocation.setAmountApplied(request.getAmount());
        allocation.setInvoice(invoice);
        payment.getAllocations().add(allocation);
        payment.setBranchId(invoice.getBranchId());

        CustomerPayment saved = customerPaymentRepository.save(payment);

        // Fourier: "all POS, Sale...module will be integrated when accounting is enabled" — see
        // AccountingService.postCustomerPayment's own javadoc.
        accountingService.postCustomerPayment(saved);

        return CustomerPaymentDto.from(saved, outstandingBalance(customer.getId()));
    }

    /**
     * Fourier: "Let record payment per invoice not per customer" — one invoice's own payment
     * history, backing its Record Payment screen; every payment that has ever applied anything to
     * this invoice (each row is one {@code CustomerPayment} — {@code distinct} only matters for a
     * legacy payment that could have targeted more than one thing at once, back when {@link
     * #recordPayment} was the only way to pay an invoice).
     */
    @Transactional(readOnly = true)
    public List<CustomerPaymentDto> paymentHistoryForInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found: " + invoiceId));
        BigDecimal currentBalance = invoice.getCustomerId() != null ? outstandingBalance(invoice.getCustomerId()) : BigDecimal.ZERO;
        return allocationRepository.findByInvoiceId(invoiceId).stream()
                .map(CustomerPaymentAllocation::getPayment)
                .distinct()
                .sorted((a, b) -> b.getPaidAt().compareTo(a.getPaidAt()))
                .map(p -> CustomerPaymentDto.from(p, currentBalance))
                .toList();
    }

    /**
     * Store-wide payment history, across every customer, most recent first — backs the new
     * Customer Payments list screen (mirrors SupplierPaymentService.paymentHistory()).
     *
     * <p>Fourier's own follow-up: "On Customer payment list no POS payment for cash sale. But when
     * I sale with Credit and record payment after it appear." Also merges in every completed,
     * customer-attached sale's own checkout tender(s) (see {@link CustomerPaymentDto#fromPosPayment}) —
     * cash, card, or any other method paid in full at time of sale now shows up here too, not just a
     * later credit settlement recorded through Record Payment. A walk-in/no-customer sale has
     * nothing to merge into and stays off this list, same as before. {@code balanceCache} avoids
     * recomputing {@link #outstandingBalance} once per row for a customer with many payments/sales —
     * it only ever changes per distinct customer id, not per row.
     */
    @Transactional(readOnly = true)
    public List<CustomerPaymentDto> paymentHistory() {
        Map<Long, BigDecimal> balanceCache = new HashMap<>();
        List<CustomerPaymentDto> rows = new ArrayList<>();
        customerPaymentRepository.findAllByOrderByPaidAtDesc().forEach(p ->
                rows.add(CustomerPaymentDto.from(p, balanceCache.computeIfAbsent(p.getCustomer().getId(), this::outstandingBalance))));
        orderRepository.findAllCompletedWithCustomer(OrderStatus.COMPLETED).forEach(order ->
                order.getPayments().forEach(payment ->
                        rows.add(CustomerPaymentDto.fromPosPayment(payment, order,
                                balanceCache.computeIfAbsent(order.getCustomer().getId(), this::outstandingBalance)))));
        return rows.stream()
                .sorted((a, b) -> b.paidAt().compareTo(a.paidAt()))
                .toList();
    }

    /**
     * Task: "Payment history". Same POS-tender merge as the store-wide {@link #paymentHistory()}
     * above (see that method's javadoc), scoped to one customer — backs the Record Payment screen's
     * own history table, which the user's follow-up reasonably extends to as well since it shows
     * the identical row shape.
     */
    @Transactional(readOnly = true)
    public List<CustomerPaymentDto> paymentHistory(Long customerId) {
        getCustomerOrThrow(customerId);
        BigDecimal currentBalance = outstandingBalance(customerId);
        List<CustomerPaymentDto> rows = new ArrayList<>();
        customerPaymentRepository.findByCustomerIdOrderByPaidAtDesc(customerId)
                .forEach(p -> rows.add(CustomerPaymentDto.from(p, currentBalance)));
        orderRepository.findCompletedForCustomer(OrderStatus.COMPLETED, customerId).forEach(order ->
                order.getPayments().forEach(payment ->
                        rows.add(CustomerPaymentDto.fromPosPayment(payment, order, currentBalance))));
        return rows.stream()
                .sorted((a, b) -> b.paidAt().compareTo(a.paidAt()))
                .toList();
    }

    /** Task: "Payment receipt" — reprinting a past payment's receipt by id. */
    @Transactional(readOnly = true)
    public CustomerPaymentDto getPaymentReceipt(Long paymentId) {
        CustomerPayment payment = customerPaymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found: " + paymentId));
        BigDecimal currentBalance = outstandingBalance(payment.getCustomer().getId());
        return CustomerPaymentDto.from(payment, currentBalance);
    }

    /**
     * Fourier: "Change payment receipt printout. Make a PDF with A4" — an A4 PDF of this one
     * payment to open in a new tab and print, same shape as InvoiceService.pdf/
     * SalesOrderService.pdf. Replaces the thermal-receipt-styled {@code window.print()} views
     * {@code InvoiceRecordPaymentComponent}/{@code RecordPaymentComponent} used to render.
     */
    @Transactional(readOnly = true)
    public byte[] pdf(Long paymentId) {
        return documentPdfService.paymentReceiptPdf(getPaymentReceipt(paymentId), storeSettingsService.getSettingsEntity());
    }

    private Customer getCustomerOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
    }

    /**
     * Fourier: "CANCEL/CREDIT PAID INVOICE → Handle the payment → ... → Customer Credit" — the one
     * place {@code Customer.creditBalance} is ever written, called only from {@code
     * InvoiceService.cancelPaidInvoice}'s CUSTOMER_CREDIT branch. Deliberately separate from this
     * class's own "no floating/unapplied customer credit balance" design for *payments* (see class
     * javadoc) — this tracks the opposite direction, what the store owes the customer, and nothing
     * here spends it automatically yet; it's a running balance to show, same as {@link
     * #refundDueForSalesOrder}'s own "informational, not a payout the system processes" caveat,
     * just persisted rather than computed.
     *
     * <p>Fourier: "Unexpected error: Cannot invoke ... because the return value of
     * Customer.getCreditBalance() is null" — {@code creditBalance} is a brand-new column added
     * under {@code ddl-auto=update}; that only adds the column, it never backfills a value onto
     * rows that already existed, so every customer created before this release reads back {@code
     * null} here despite the entity's own {@code = BigDecimal.ZERO} field initializer (which only
     * applies to an object built fresh in the JVM, never to a row loaded from the database). Same
     * null-safe-then-zero read {@code AccountingService.postPosSale} already uses for {@code
     * SaleOrder.creditAmount} — no migration needed, since a null here was always a valid JPA
     * state for this nullable {@code BigDecimal} column (unlike a primitive {@code boolean}
     * column, e.g. {@code Customer.walkIn}, which does need an explicit backfill — see {@code
     * DataSeeder.backfillWalkInCustomer} — because a primitive can't hold a null read back from
     * the database at all).
     */
    @Transactional
    public void addCustomerCredit(Customer customer, BigDecimal amount) {
        BigDecimal current = customer.getCreditBalance() != null ? customer.getCreditBalance() : BigDecimal.ZERO;
        customer.setCreditBalance(current.add(amount));
        customerRepository.save(customer);
    }
}
