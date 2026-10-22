package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RestaurantOrderLineRepository extends JpaRepository<RestaurantOrderLine, Long> {
    /** Dishes that became ready inside a window — what the preparation-time averages are worked out from. */
    List<RestaurantOrderLine> findByReadyAtBetween(Instant from, Instant to);
}
