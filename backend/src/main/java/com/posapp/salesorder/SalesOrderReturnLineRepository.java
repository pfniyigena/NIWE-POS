package com.posapp.salesorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalesOrderReturnLineRepository extends JpaRepository<SalesOrderReturnLine, Long> {

    /**
     * How much of one Sales Order line has already been returned, across every return visit —
     * guards {@link SalesOrderReturnService#processReturn} against over-returning a line (same
     * shape as {@code com.posapp.returns.SalesReturnLineRepository.sumReturnedQuantityForLine}).
     */
    @Query("select coalesce(sum(l.quantity), 0) from SalesOrderReturnLine l where l.orderLine.id = :lineId")
    int sumReturnedQuantityForLine(@Param("lineId") Long lineId);

    /**
     * Every return line against any line on this Sales Order, across every separate return visit —
     * mirrors {@code SalesReturnLineRepository.findByOrderId}; backs the "already returned" column
     * on the order detail screen's return panel.
     */
    @Query("select l from SalesOrderReturnLine l where l.orderLine.salesOrder.id = :salesOrderId")
    List<SalesOrderReturnLine> findBySalesOrderId(@Param("salesOrderId") Long salesOrderId);
}
