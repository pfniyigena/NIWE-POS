package com.posapp.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByOrderByInvoiceDateDesc();

    /**
     * The one invoice (of any status — VOID included) for this Sales Order, if one has ever been
     * generated. Exactly one invoice document is ever created per Sales Order in this app — voiding
     * it doesn't free up the order for a replacement (see {@code Invoice}'s class javadoc) — so
     * {@code InvoiceService.generate} uses this to refuse a second attempt outright, and the Sales
     * Order detail screen uses it to show "View invoice" instead of "Generate invoice" once one
     * exists.
     */
    Optional<Invoice> findBySalesOrderId(Long salesOrderId);

    /** Every invoice ever issued to one customer — Sales-Order-derived and standalone alike — oldest first; backs {@code CustomerCreditService.listOutstandingOrders}/{@code outstandingBalance}'s standalone-invoice sweep. */
    List<Invoice> findByCustomerIdOrderByInvoiceDateAsc(Long customerId);

    /** Same invoices as above, newest first — backs the Invoices screen's own {@code ?customerId=} filter (see InvoiceService.listAll's javadoc). */
    List<Invoice> findByCustomerIdOrderByInvoiceDateDesc(Long customerId);

    /** Every voided invoice, store-wide, newest-voided first — backs {@code InvoiceService.listCreditNotes}. */
    List<Invoice> findByStatusOrderByVoidedAtDesc(InvoiceStatus status);

    /** Fourier: "Subscriptions Module" — every invoice ever generated for one subscription, newest first; backs its own billing-history view. */
    List<Invoice> findBySubscriptionIdOrderByInvoiceDateDesc(Long subscriptionId);

    /** Fourier: "Add Recurring Invoice" — every invoice ever generated for one recurring invoice template, newest first; backs its own "Generated invoices" history view. */
    List<Invoice> findByRecurringInvoiceTemplateIdOrderByInvoiceDateDesc(Long recurringInvoiceTemplateId);
}
