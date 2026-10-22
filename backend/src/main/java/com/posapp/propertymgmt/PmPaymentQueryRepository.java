package com.posapp.propertymgmt;

import com.posapp.credit.CustomerPaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Reads the payments recorded on invoices, to see what rent was collected in a period. Read only. */
public interface PmPaymentQueryRepository extends JpaRepository<CustomerPaymentAllocation, Long> {
    @Query("select a from CustomerPaymentAllocation a join fetch a.payment p where a.invoice.id in :invoiceIds and p.paidAt >= :from and p.paidAt < :to")
    List<CustomerPaymentAllocation> collectedOn(@Param("invoiceIds") Collection<Long> invoiceIds, @Param("from") Instant from, @Param("to") Instant to);

    /** Every payment applied to these invoices, whenever it was made. */
    @Query("select a from CustomerPaymentAllocation a join fetch a.payment p where a.invoice.id in :invoiceIds")
    List<CustomerPaymentAllocation> forInvoices(@Param("invoiceIds") Collection<Long> invoiceIds);

    /** What one payment was applied to. */
    @Query("select a from CustomerPaymentAllocation a join fetch a.payment p where p.id = :paymentId and a.invoice is not null")
    List<CustomerPaymentAllocation> ofPayment(@Param("paymentId") Long paymentId);
}
