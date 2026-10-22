package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RestaurantDayCloseRepository extends JpaRepository<RestaurantDayClose, Long> {
    Optional<RestaurantDayClose> findByBusinessDateAndStatus(LocalDate date, String status);

    List<RestaurantDayClose> findAllByOrderByBusinessDateDescIdDesc();

    Optional<RestaurantDayClose> findFirstByStatusAndBusinessDateLessThanOrderByBusinessDateDesc(String status, LocalDate date);

    Optional<RestaurantDayClose> findFirstByBusinessDateAndStatusOrderByIdDesc(LocalDate date, String status);

    @org.springframework.data.jpa.repository.Query("select coalesce(max(c.zNumber), 0) from RestaurantDayClose c")
    int maxZ();
}
