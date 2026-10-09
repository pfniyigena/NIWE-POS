package com.posapp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findAllByOrderByExpenseDateDesc();

    /**
     * The Expenses reports' raw input (segmented and periodic alike — see
     * AccountingReportService.expensesGrouped). Filtered to APPROVED only (Fourier: "Approval
     * workflow before posting") — a PENDING expense never posted to Accounting in the first
     * place, a REJECTED one never will, and a VOIDED one's posting has since been reversed, so
     * none of the three belong in a financial report alongside real, standing postings.
     */
    List<Expense> findByStatusAndExpenseDateBetweenOrderByExpenseDateAsc(ExpenseStatus status, LocalDate from, LocalDate to);

    /** Fourier: "recurring expenses" — every expense a given template has ever generated, newest first, for that template's own detail view. */
    List<Expense> findByRecurringTemplateIdOrderByExpenseDateDesc(Long recurringTemplateId);
}
