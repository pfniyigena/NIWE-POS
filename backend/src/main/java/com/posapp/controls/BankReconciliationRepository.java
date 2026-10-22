package com.posapp.controls;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, Long> {
    List<BankReconciliation> findAllByOrderByStatementDateDescIdDesc();

    Optional<BankReconciliation> findFirstByAccountIdAndStatus(Long accountId, String status);

    Optional<BankReconciliation> findFirstByAccountIdAndStatusOrderByStatementDateDescIdDesc(Long accountId, String status);
}
