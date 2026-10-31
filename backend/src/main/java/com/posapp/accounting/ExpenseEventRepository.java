package com.posapp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseEventRepository extends JpaRepository<ExpenseEvent, Long> {
    List<ExpenseEvent> findByExpenseIdOrderByIdDesc(Long expenseId);

    void deleteByExpenseId(Long expenseId);
}
