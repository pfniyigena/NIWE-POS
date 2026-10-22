package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LinenLogRepository extends JpaRepository<LinenLog, Long> {

    java.util.List<LinenLog> findByLogDateBetweenOrderByLogDateDescIdDesc(java.time.LocalDate from, java.time.LocalDate to);
}
