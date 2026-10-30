package com.posapp.installment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentFollowUpRepository extends JpaRepository<InstallmentFollowUp, Long> {

    List<InstallmentFollowUp> findByPlanIdOrderByCreatedAtDescIdDesc(Long planId);
}
