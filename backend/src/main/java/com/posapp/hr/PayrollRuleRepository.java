package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRuleRepository extends JpaRepository<PayrollRule, Long> {
    java.util.List<PayrollRule> findAllByOrderBySortOrderAscIdAsc();

    java.util.List<PayrollRule> findByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByCode(String code);
}
