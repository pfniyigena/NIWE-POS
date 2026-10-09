package com.posapp.packaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PackagingBreakdownRepository extends JpaRepository<PackagingBreakdown, Long> {

    List<PackagingBreakdown> findAllByOrderByCreatedAtDesc();

    List<PackagingBreakdown> findByRuleIdOrderByCreatedAtDesc(Long ruleId);

    /** Fourier: "Breakdown should visible into Stock movement" — ReportService.stockMovementHistory's own entry point into this module. */
    List<PackagingBreakdown> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
}
