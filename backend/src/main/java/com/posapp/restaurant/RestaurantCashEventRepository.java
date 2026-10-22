package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RestaurantCashEventRepository extends JpaRepository<RestaurantCashEvent, Long> {
    List<RestaurantCashEvent> findByCreatedAtBetweenOrderByCreatedAtAscIdAsc(Instant from, Instant to);
}
