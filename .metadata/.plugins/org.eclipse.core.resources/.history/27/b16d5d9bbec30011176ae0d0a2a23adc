package com.posapp.recurringinvoice;

import java.time.LocalDate;

/**
 * How often a {@link RecurringInvoiceTemplate} generates its next {@code
 * com.posapp.invoice.Invoice} — Fourier: "Add Recurring Invoice". Four fixed cadences, not a
 * general cron expression — same "fixed set of cadences, not a free-form schedule" choice {@code
 * com.posapp.subscription.BillingInterval} already made for customer subscriptions (and {@code
 * com.posapp.recurringexpense.RecurrenceFrequency} made, with only two, for the business's own
 * recurring costs); a recurring invoice realistically needs the same QUARTERLY/YEARLY cadences a
 * subscription does. {@link #addTo} computes the next run date via {@code LocalDate}'s own
 * plusWeeks/plusMonths/plusYears, which already clamps a month-end date correctly (e.g. Jan 31 +
 * 1 month lands on Feb 28/29, not an invalid Feb 31) with no extra day-of-month bookkeeping
 * needed.
 */
public enum RecurrenceFrequency {
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
}
