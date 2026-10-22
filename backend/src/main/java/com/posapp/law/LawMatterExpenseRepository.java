package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawMatterExpenseRepository extends JpaRepository<LawMatterExpense, Long> {
    List<LawMatterExpense> findByMatterIdOrderByExpenseDateDescIdDesc(Long matterId);

    List<LawMatterExpense> findByBillId(Long billId);

    long countByMatterId(Long matterId);

    void deleteByMatterId(Long matterId);
}
