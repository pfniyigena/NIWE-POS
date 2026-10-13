package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Row types of the Instalment reports, used by {@code InstallmentReportService} and the matching {@code ReportController} endpoints. */
public final class InstallmentReportRows {

    private InstallmentReportRows() {
    }

    public record AgreementRow(String planNumber, String customer, String invoiceNumber, String status, int instalments, int paidInstalments,
                               BigDecimal financed, BigDecimal interest, BigDecimal totalPayable, BigDecimal paid, BigDecimal outstanding,
                               BigDecimal overdue, LocalDate nextDue) {
    }

    public record DueRow(String planNumber, String customer, String invoiceNumber, String instalment, LocalDate dueDate, BigDecimal totalDue,
                         BigDecimal paid, BigDecimal balance, String status, long daysOverdue) {
    }

    public record CollectionRow(Instant paidAt, String receiptNumber, String planNumber, String customer, BigDecimal principal,
                                BigDecimal interestAndFees, BigDecimal total, String method, String recordedBy) {
    }
}
