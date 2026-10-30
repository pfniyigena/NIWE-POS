package com.posapp.subscription.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read-only views: overview numbers, proration quote, settings. */
public final class SubscriptionViews {
    private SubscriptionViews() {
    }

    public record UpcomingDto(Long subscriptionId, String customerName, String planName, LocalDate date, BigDecimal amount, boolean onTrial) {
    }

    public record UnpaidDto(Long subscriptionId, String customerName, String invoiceNumber, BigDecimal outstanding, long daysLate, int reminders) {
    }

    public record SummaryDto(int active, int onTrial, int paused, int cancelled, int ended,
                             BigDecimal mrr, BigDecimal arr, BigDecimal averagePerSubscription,
                             int newLast30, int cancelledLast30, BigDecimal churnPercent,
                             int upcoming7Count, BigDecimal upcoming7Amount, int upcoming30Count, BigDecimal upcoming30Amount,
                             int trialsEnding7, int scheduledCancels, int termsEnding,
                             int unpaidCount, BigDecimal unpaidAmount,
                             List<UpcomingDto> upcoming, List<UnpaidDto> unpaid) {
    }

    /** What a plan change would do, before it is done. Amounts are before tax. */
    public record ChangeQuoteDto(boolean prorated, long remainingDays, long periodDays, BigDecimal oldCycleAmount, BigDecimal newCycleAmount,
                                 BigDecimal chargeNow, BigDecimal creditAdded, BigDecimal monthlyBefore, BigDecimal monthlyAfter, String message) {
    }

    public record SettingsDto(boolean dunningEnabled, int dunningFirstDays, int dunningRepeatDays, int dunningMax, int catchUpMax) {
    }
}
