package com.posapp.installment;

/** ACTIVE: being paid off. COMPLETED: everything (principal, interest, fees) settled. CANCELLED: the plan was stopped; whatever is still unpaid stays on the invoice as an ordinary debt. */
public enum InstallmentPlanStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}
