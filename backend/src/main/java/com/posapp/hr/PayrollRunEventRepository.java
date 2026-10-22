package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollRunEventRepository extends JpaRepository<PayrollRunEvent, Long> {
    List<PayrollRunEvent> findByRunIdOrderByIdDesc(Long runId);
}
