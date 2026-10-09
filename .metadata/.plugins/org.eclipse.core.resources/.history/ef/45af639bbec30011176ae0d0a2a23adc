package com.posapp.subscription;

import java.time.LocalDate;

/**
 * How often a {@link Subscription} bills — Fourier: "Subscriptions Module", scoped via
 * AskUserQuestion to customer subscriptions billed against a fixed plan. Four fixed cadences, not
 * a general cron expression — same "fixed set of cadences, not a free-form schedule" choice
 * {@code com.posapp.recurringexpense.RecurrenceFrequency} already made for the business's own
 * recurring bills (that enum only offers two; this one adds QUARTERLY/YEARLY since a customer
 * membership/service plan realistically needs those too). {@link #addTo} computes the next
 * billing date via {@code LocalDate}'s own plusWeeks/plusMonths/plusYears, which already clamps a
 * month-end date correctly (e.g. Jan 31 + 1 month lands on Feb 28/29, not an invalid Feb 31) with
 * no extra day-of-month bookkeeping needed. {@link #subtractFrom} is its exact inverse, same
 * clamping behavior via minusWeeks/minusMonths/minusYears — used by {@code
 * SubscriptionService.billOne} to find where a "paid after service" cycle actually started, given
 * only the date it's now due.
 */
public enum BillingInterval {
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY;

    public LocalDate addTo(LocalDate date) {
        return switch (this) {
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case QUARTERLY -> date.plusMonths(3);
            case YEARLY -> date.plusYears(1);
        };
    }

    public LocalDate subtractFrom(LocalDate date) {
        return switch (this) {
            case WEEKLY -> date.minusWeeks(1);
            case MONTHLY -> date.minusMonths(1);
            case QUARTERLY -> date.minusMonths(3);
            case YEARLY -> date.minusYears(1);
        };
    }
}
