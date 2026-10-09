package com.posapp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseReturnLineRepository extends JpaRepository<PurchaseReturnLine, Long> {

    /**
     * How much of one Purchase Order line has already been returned, across every return visit —
     * guards {@link PurchaseReturnService#processReturn} against over-returning a line (same shape
     * as {@code com.posapp.salesorder.SalesOrderReturnLineRepository.sumReturnedQuantityForLine}).
     */
    @Query("select coalesce(sum(l.quantity), 0) from PurchaseReturnLine l where l.orderLine.id = :lineId")
    int sumReturnedQuantityForLine(@Param("lineId") Long lineId);

    /**
     * Every return line against any line on this Purchase Order, across every separate return
     * visit — backs the "already returned" column on the order detail screen's return panel.
     */
    @Query("select l from PurchaseReturnLine l where l.orderLine.purchaseOrder.id = :purchaseOrderId")
    List<PurchaseReturnLine> findByPurchaseOrderId(@Param("purchaseOrderId") Long purchaseOrderId);
}
