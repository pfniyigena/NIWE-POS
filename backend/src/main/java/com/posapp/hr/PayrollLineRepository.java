package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Payroll lines looked up by employee: the employee's own payslips. */
public interface PayrollLineRepository extends JpaRepository<PayrollLine, Long> {

    List<PayrollLine> findByEmployeeIdAndRunStatusInOrderByRunPeriodYearDescRunPeriodMonthDesc(Long employeeId, Collection<PayrollStatus> statuses);

    Optional<PayrollLine> findByRunIdAndEmployeeIdAndRunStatusIn(Long runId, Long employeeId, Collection<PayrollStatus> statuses);
}
