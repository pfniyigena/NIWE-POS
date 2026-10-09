package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RestaurantWastageRepository extends JpaRepository<RestaurantWastage, Long> {

    List<RestaurantWastage> findAllByOrderByCreatedAtDesc();

    /** Backs the dedicated Restaurant Wastage report — every entry logged in the range, any status. */
    List<RestaurantWastage> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    /**
     * Backs ReportService.stockMovementHistory's "Restaurant wastage" rows specifically — filtered
     * to {@link RestaurantWastageStatus#CONFIRMED} (a PENDING/REJECTED entry never touched stock)
     * and ranged by {@code confirmedAt}, not {@code createdAt}, since that's when the stock
     * movement this report shows actually happened — see RestaurantWastage's own javadoc.
     */
    List<RestaurantWastage> findByStatusAndConfirmedAtBetweenOrderByConfirmedAtDesc(RestaurantWastageStatus status, Instant from, Instant to);
}
