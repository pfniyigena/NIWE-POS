package com.posapp.credit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CustomerPaymentAllocationRepository extends JpaRepository<CustomerPaymentAllocation, Long> {

    /** Fourier: "Let record payment per invoice not per customer" — every allocation ever recorded against one invoice, backing that invoice's own payment history (see {@code CustomerCreditService.paymentHistoryForInvoice}). */
    List<CustomerPaymentAllocation> findByInvoiceId(Long invoiceId);

    /**
     * How much of one order's {@code creditAmount} has already been paid off, across every
     * allocation ever recorded against it — the one query {@code
     * CustomerCreditService.outstandingForOrder} builds every "outstanding" figure in this app
     * from (checkout's credit-limit check, the allocation-suggestion screen, the Credit Sale and
     * Outstanding/Overdue Credit reports). {@code coalesce(..., 0)} so an order with no
     * allocations yet returns zero rather than null.
     */
    @Query("select coalesce(sum(a.amountApplied), 0) from CustomerPaymentAllocation a where a.order.id = :orderId")
    BigDecimal sumAppliedForOrder(@Param("orderId") Long orderId);

    /** The legacy Sales Order mirror of {@link #sumAppliedForOrder} — see CustomerPaymentAllocation's "exclusive arc" javadoc; only ever nonzero for a row written before invoices carried payment. */
    @Query("select coalesce(sum(a.amountApplied), 0) from CustomerPaymentAllocation a where a.salesOrder.id = :salesOrderId")
    BigDecimal sumAppliedForSalesOrder(@Param("salesOrderId") Long salesOrderId);

    /** The Invoice mirror of {@link #sumAppliedForOrder} — every new back-office payment's own target, see CustomerPaymentAllocation's "exclusive arc" javadoc. */
    @Query("select coalesce(sum(a.amountApplied), 0) from CustomerPaymentAllocation a where a.invoice.id = :invoiceId")
    BigDecimal sumAppliedForInvoice(@Param("invoiceId") Long invoiceId);
}
