package com.posapp.manufacturing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ManufacturingOrderRunRepository extends JpaRepository<ManufacturingOrderRun, Long> {

    /** Used by the production lines of the general reports (ReportService). */
    List<ManufacturingOrderRun> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    /** MF-5: the production runs recorded in a period, oldest first. */
    List<ManufacturingOrderRun> findByCreatedAtBetweenOrderByCreatedAtAscIdAsc(Instant from, Instant to);
}
