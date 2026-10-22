package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollRemittanceRepository extends JpaRepository<PayrollRemittance, Long> {
    List<PayrollRemittance> findAllByOrderByPaidOnDescIdDesc();
    List<PayrollRemittance> findByStatus(String status);
}
