package com.posapp.salesorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SalesOrderFulfillmentRepository extends JpaRepository<SalesOrderFulfillment, Long> {

    /** Powers ReportService.stockMovementHistory — see com.posapp.inventory.StockReceiptRepository's sibling method for the same shape. */
    List<SalesOrderFulfillment> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    /** Every fulfilment event of one order, oldest first — backs SalesOrderDto.fulfilledFrom. */
    List<SalesOrderFulfillment> findBySalesOrderOrderByCreatedAtAsc(SalesOrder salesOrder);

    /** Multi-warehouse phase 3 — the most recent fulfilment of an order, used to default where a return puts the goods back. */
    java.util.Optional<SalesOrderFulfillment> findFirstBySalesOrderOrderByCreatedAtDesc(SalesOrder salesOrder);
}
