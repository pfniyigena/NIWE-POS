package com.posapp.installment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface InstallmentPaymentRepository extends JpaRepository<InstallmentPayment, Long> {

    List<InstallmentPayment> findByPlanIdOrderByPaidAtDescIdDesc(Long planId);

    List<InstallmentPayment> findByPaidAtBetweenOrderByPaidAtDescIdDesc(Instant from, Instant to);

    @Query("select coalesce(sum(p.chargesPart), 0) from InstallmentPayment p where p.plan.id = :planId")
    BigDecimal sumChargesPart(@Param("planId") Long planId);
}
