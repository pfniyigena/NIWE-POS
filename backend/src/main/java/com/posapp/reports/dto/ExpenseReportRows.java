package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Row types for the Expenses reports. */
public final class ExpenseReportRows {
    private ExpenseReportRows() {
    }

    public record RegisterRow(Long id, String expenseNumber, LocalDate date, String payee, String category, String costCenter, String reference, String status,
                              BigDecimal amount, BigDecimal tax, BigDecimal net, boolean paid, String paidFrom, LocalDate dueDate, BigDecimal balance,
                              String enteredBy, String approvedBy) {
    }

    public record AgingRow(Long id, String expenseNumber, String payee, String reference, LocalDate billDate, LocalDate dueDate, long daysOverdue,
                           String bucket, BigDecimal amount, BigDecimal paidSoFar, BigDecimal balance) {
    }

    public record BudgetRow(String month, String label, String kind, BigDecimal budget, BigDecimal spent, BigDecimal pending, BigDecimal remaining, Double percentUsed, String state) {
    }

    public record PaymentAccountRow(String account, long paidAtOnce, long billPayments, BigDecimal paidAtOnceAmount, BigDecimal billPaymentsAmount, BigDecimal total) {
    }
}
