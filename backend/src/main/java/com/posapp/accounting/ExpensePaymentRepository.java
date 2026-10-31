package com.posapp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpensePaymentRepository extends JpaRepository<ExpensePayment, Long> {
    List<ExpensePayment> findByExpenseIdOrderByPaidOnAscIdAsc(Long expenseId);

    @org.springframework.data.jpa.repository.Query("select p from ExpensePayment p where p.paidOn between :from and :to order by p.paidOn asc, p.id asc")
    List<ExpensePayment> findBetween(@org.springframework.data.repository.query.Param("from") java.time.LocalDate from, @org.springframework.data.repository.query.Param("to") java.time.LocalDate to);
}
