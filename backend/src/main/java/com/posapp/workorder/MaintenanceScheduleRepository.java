package com.posapp.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, Long> {
    List<MaintenanceSchedule> findAllByOrderByNextDueDateAscIdAsc();

    List<MaintenanceSchedule> findByActiveTrueAndNextDueDateLessThanEqual(LocalDate date);
}
