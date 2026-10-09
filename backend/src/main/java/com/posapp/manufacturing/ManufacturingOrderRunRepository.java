package com.posapp.manufacturing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ManufacturingOrderRunRepository extends JpaRepository<ManufacturingOrderRun, Long> {

    /** Task: "Any transaction affecting stock should be shown to Stock Movement History" — powers ReportService.stockMovementHistory's two Manufacturing sources (output + consumption, the latter via each run's own consumptions). */
    List<ManufacturingOrderRun> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
}
