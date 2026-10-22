package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffLoanRepository extends JpaRepository<StaffLoan, Long> {
    List<StaffLoan> findAllByOrderByIdDesc();
    List<StaffLoan> findByStatus(LoanStatus status);
}
