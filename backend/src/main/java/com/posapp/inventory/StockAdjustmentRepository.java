package com.posapp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    List<StockAdjustment> findAllByOrderByCreatedAtDesc();

    /** Task 1: "Inventory Report" (Stock movement history) — see ReportService.stockMovementHistory. */
    List<StockAdjustment> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
}
