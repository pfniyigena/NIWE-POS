package com.posapp.reports.dto;

import java.time.LocalDate;

/** Rows of the law firm reports. Restricted matters show no title or client. */
public final class LawReportRows {

    private LawReportRows() {
    }

    public record MatterRow(String number, String title, String client, String practiceArea, String status, String lead, LocalDate opened, LocalDate closed, String billing,
                            int openTasks, LocalDate nextDeadline) {
    }

    public record DeadlineRow(LocalDate due, String time, String type, String status, String title, String matter, String client, String assignedTo, String overdue) {
    }

    /** What a matter has earned, billed and still has to bill. */
    public record BillingRow(String number, String title, String client, String billing, String status, java.math.BigDecimal hours, java.math.BigDecimal unbilledTime,
                             java.math.BigDecimal unbilledCosts, java.math.BigDecimal stagesPlanned, java.math.BigDecimal billed, java.math.BigDecimal paid,
                             java.math.BigDecimal outstanding, java.math.BigDecimal retainerBalance) {
    }

    /** One bill made in a period. */
    public record FeeRow(LocalDate date, String matter, String title, String client, String kind, String invoice, String invoiceStatus, java.math.BigDecimal fees,
                         java.math.BigDecimal costs, java.math.BigDecimal retainerApplied, java.math.BigDecimal amount, java.math.BigDecimal paid, java.math.BigDecimal outstanding) {
    }

    public record TrustBalancesRow(String matter, String title, String client, String status, java.math.BigDecimal balance, java.math.BigDecimal pending, LocalDate lastActivity) {
    }

    public record TrustLedgerRow(LocalDate date, String number, String matter, String client, String type, String party, String reference, java.math.BigDecimal moneyIn,
                                 java.math.BigDecimal moneyOut, java.math.BigDecimal balance) {
    }

    public record WorkloadRow(String lawyer, int mattersLed, int mattersOn, int openTasks, int overdueTasks, int deadlinesNext30, int overdueDeadlines) {
    }
}
