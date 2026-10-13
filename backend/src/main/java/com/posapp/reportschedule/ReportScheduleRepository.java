package com.posapp.reportschedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {

    List<ReportSchedule> findAllByOrderByNameAsc();

    List<ReportSchedule> findByActiveTrue();
}
