package com.posapp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseAttachmentRepository extends JpaRepository<ExpenseAttachment, Long> {
    List<ExpenseAttachment> findByExpenseIdOrderByIdAsc(Long expenseId);

    long countByExpenseId(Long expenseId);

    void deleteByExpenseId(Long expenseId);

    @org.springframework.data.jpa.repository.Query("select a.expenseId, count(a) from ExpenseAttachment a group by a.expenseId")
    List<Object[]> countsByExpense();
}
