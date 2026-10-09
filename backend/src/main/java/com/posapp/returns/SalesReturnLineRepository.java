package com.posapp.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SalesReturnLineRepository extends JpaRepository<SalesReturnLine, Long> {

    @Query("select coalesce(sum(l.quantity), 0) from SalesReturnLine l where l.orderLine.id = :orderLineId")
    int sumReturnedQuantityForLine(Long orderLineId);

    /**
     * Every return line against any line on this order, across every separate return visit — used
     * by ReturnsService.returnedQuantitiesForOrder to mark returned items on the original sale
     * receipt (Task: "on the receipt which was returned, I should mark it as returned"). Grouped
     * and summed in Java rather than in this query, matching this codebase's existing "load the
     * matching rows, aggregate in the service" convention (see ReportService).
     */
    @Query("select l from SalesReturnLine l where l.orderLine.order.id = :orderId")
    List<SalesReturnLine> findByOrderId(Long orderId);
}
