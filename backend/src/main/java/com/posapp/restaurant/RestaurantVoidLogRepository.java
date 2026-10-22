package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestaurantVoidLogRepository extends JpaRepository<RestaurantVoidLog, Long> {
    List<RestaurantVoidLog> findAllByOrderByCreatedAtDescIdDesc();
}
