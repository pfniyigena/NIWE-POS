package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffLoanRepaymentRepository extends JpaRepository<StaffLoanRepayment, Long> {
    List<StaffLoanRepayment> findByLoanIdOrderByRepaidOnAscIdAsc(Long loanId);
    List<StaffLoanRepayment> findByRunId(Long runId);
    List<StaffLoanRepayment> findAll();
}
