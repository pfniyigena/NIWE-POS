package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    java.util.List<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc();

    java.util.Optional<PayrollRun> findByPeriodYearAndPeriodMonth(int year, int month);
}
