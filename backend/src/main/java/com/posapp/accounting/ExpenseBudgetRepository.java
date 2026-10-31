package com.posapp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseBudgetRepository extends JpaRepository<ExpenseBudget, Long> {
    List<ExpenseBudget> findByBudgetYearOrderByIdAsc(int budgetYear);

    Optional<ExpenseBudget> findByAccountIdAndBudgetYear(Long accountId, int budgetYear);

    Optional<ExpenseBudget> findByCostCenterIdAndBudgetYear(Long costCenterId, int budgetYear);
}
