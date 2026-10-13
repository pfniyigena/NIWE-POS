package com.posapp.installment;

/** Where one instalment stands today (worked out, never stored). */
public enum InstallmentState {
    PAID,
    PARTIAL,
    PENDING,
    OVERDUE
}
