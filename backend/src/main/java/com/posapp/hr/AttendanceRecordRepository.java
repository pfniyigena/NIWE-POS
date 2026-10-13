package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    java.util.List<AttendanceRecord> findByWorkDateBetweenOrderByWorkDateAscIdAsc(java.time.LocalDate from, java.time.LocalDate to);

    java.util.List<AttendanceRecord> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(Long employeeId, java.time.LocalDate from, java.time.LocalDate to);

    java.util.Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, java.time.LocalDate workDate);
}
