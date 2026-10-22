package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawTrustReconciliationRepository extends JpaRepository<LawTrustReconciliation, Long> {
    List<LawTrustReconciliation> findAllByOrderByAsOfDateDescIdDesc();
}
