package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OnlineClosedDayRepository extends JpaRepository<OnlineClosedDay, Long> {
    List<OnlineClosedDay> findAllByDayGreaterThanEqualOrderByDayAsc(LocalDate from);

    boolean existsByDay(LocalDate day);
}
