package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RestaurantOnlineOrderRepository extends JpaRepository<RestaurantOnlineOrder, Long> {
    Optional<RestaurantOnlineOrder> findByCode(String code);

    List<RestaurantOnlineOrder> findAllByOrderByCreatedAtDescIdDesc();

    List<RestaurantOnlineOrder> findByStatusOrderByCreatedAtAsc(String status);

    long countByStatus(String status);

    long countByStatusAndCustomerPhoneIn(String status, List<String> phones);

    long countByStatusAndTableId(String status, Long tableId);

    List<RestaurantOnlineOrder> findByStatusAndCreatedAtBefore(String status, Instant before);
}
