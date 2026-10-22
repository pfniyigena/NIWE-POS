package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeFinalPayRepository extends JpaRepository<EmployeeFinalPay, Long> {
    Optional<EmployeeFinalPay> findByEmployeeId(Long employeeId);
}
