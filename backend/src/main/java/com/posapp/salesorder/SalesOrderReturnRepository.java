package com.posapp.salesorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SalesOrderReturnRepository extends JpaRepository<SalesOrderReturn, Long> {

    List<SalesOrderReturn> findAllByOrderByReturnDateDesc();

    List<SalesOrderReturn> findBySalesOrderIdOrderByReturnDateDesc(Long salesOrderId);

    /** Task 6: "Stock Movement History" — powers ReportService.stockMovementHistory. */
    List<SalesOrderReturn> findByReturnDateBetweenOrderByReturnDateDesc(Instant from, Instant to);

    /**
     * How much has ever been returned against one Sales Order, across every return visit — the
     * one figure {@code CustomerCreditService.outstandingForSalesOrder}/{@code
     * refundDueForSalesOrder} both build on (see their javadoc), the same "one query everything
     * reuses" shape as {@code CustomerPaymentAllocationRepository.sumAppliedForSalesOrder}.
     */
    @Query("select coalesce(sum(r.refundTotal), 0) from SalesOrderReturn r where r.salesOrder.id = :salesOrderId")
    BigDecimal sumRefundTotalForSalesOrder(@Param("salesOrderId") Long salesOrderId);
}
