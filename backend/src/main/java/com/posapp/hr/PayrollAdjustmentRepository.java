package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Long> {
    List<PayrollAdjustment> findByPeriodYearAndPeriodMonthOrderByIdAsc(int year, int month);
    List<PayrollAdjustment> findAllByOrderByPeriodYearDescPeriodMonthDescIdDesc();
    List<PayrollAdjustment> findByEmployeeIdAndSource(Long employeeId, String source);
}
