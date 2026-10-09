package com.posapp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {

    /** Powers ReportService.stockMovementHistory — see StockAdjustmentRepository's sibling method for the same shape. */
    List<StockReceipt> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
}
