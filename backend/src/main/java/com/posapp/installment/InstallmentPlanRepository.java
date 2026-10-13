package com.posapp.installment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, Long> {

    List<InstallmentPlan> findAllByOrderByIdDesc();

    List<InstallmentPlan> findByStatus(InstallmentPlanStatus status);

    List<InstallmentPlan> findByInvoiceIdOrderByIdDesc(Long invoiceId);

    boolean existsByInvoiceIdAndStatus(Long invoiceId, InstallmentPlanStatus status);
}
