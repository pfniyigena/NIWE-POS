package com.posapp.installment;

/**
 * ACTIVE: being paid off. COMPLETED: everything (principal, interest, fees) settled. CANCELLED: the plan was stopped; whatever is still unpaid
 * stays on the invoice as an ordinary debt. DEFAULTED: the customer has stopped paying — no more late fees or reminders, payments are still taken.
 * WRITTEN_OFF: the unpaid principal was written off as a bad debt.
 */
public enum InstallmentPlanStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
    DEFAULTED,
    WRITTEN_OFF
}
