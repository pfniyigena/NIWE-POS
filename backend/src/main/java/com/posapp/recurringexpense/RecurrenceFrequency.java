package com.posapp.recurringexpense;

/**
 * How often a {@link RecurringExpenseTemplate} generates its next {@code
 * com.posapp.accounting.Expense} (Fourier: "Recurring expenses" — rent, salaries, subscriptions,
 * loan payments). Deliberately just two fixed cadences rather than a general cron expression —
 * {@code RecurringExpenseTemplateService#advance} computes the next run date by adding exactly
 * one week or one month to the current one via {@code java.time.LocalDate.plusWeeks}/{@code
 * plusMonths}, which already clamps a month-end date correctly (e.g. Jan 31 + 1 month lands on
 * Feb 28/29, not an invalid Feb 31) with no extra day-of-month bookkeeping needed.
 */
public enum RecurrenceFrequency {
    WEEKLY,
    MONTHLY
}
