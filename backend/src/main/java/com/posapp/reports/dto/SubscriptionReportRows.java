package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Row types of the Subscription reports, used by {@code SubscriptionReportService} and the matching {@code ReportController} endpoints. */
public final class SubscriptionReportRows {

    private SubscriptionReportRows() {
    }

    /** One plan: how many subscriptions in each state and the recurring revenue they bring (before tax). */
    public record PlanRow(String plan, int active, int onTrial, int paused, int cancelledOrEnded, int seats, BigDecimal mrr, BigDecimal arr, BigDecimal averageMonthly) {
    }

    /** A subscription that was cancelled or ended in the period. */
    public record ChurnRow(String customer, String plan, String status, LocalDate started, LocalDate leftOn, long monthsActive, BigDecimal monthlyValue, int cyclesBilled, String reason) {
    }

    /** An unpaid subscription invoice. */
    public record UnpaidRow(String customer, String plan, String invoiceNumber, LocalDate dueOn, long daysLate, BigDecimal total, BigDecimal outstanding, int reminders, String subscriptionStatus) {
    }
}
