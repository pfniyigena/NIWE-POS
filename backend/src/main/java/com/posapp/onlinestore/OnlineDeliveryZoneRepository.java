package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnlineDeliveryZoneRepository extends JpaRepository<OnlineDeliveryZone, Long> {
    List<OnlineDeliveryZone> findAllByOrderBySortOrderAscNameAsc();
}
