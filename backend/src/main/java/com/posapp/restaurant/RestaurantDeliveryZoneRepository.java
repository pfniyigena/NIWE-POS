package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestaurantDeliveryZoneRepository extends JpaRepository<RestaurantDeliveryZone, Long> {
    List<RestaurantDeliveryZone> findAllByOrderBySortOrderAscNameAsc();
}
