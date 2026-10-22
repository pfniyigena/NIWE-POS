package com.posapp.realestate;

/** Where a commission share stands on its way to the person who earned it. */
public enum PayoutState {
    /** The sale is not complete yet, so nothing is earned. */
    PLANNED,
    /** Earned, not yet sent to the accounts. */
    TO_POST,
    /** Sent to Expenses, waiting for someone to approve it. */
    AWAITING_APPROVAL,
    /** Approved, not yet paid out. */
    TO_PAY,
    PAID,
    /** The expense was rejected or voided (or is gone): the share has to be posted again. */
    REJECTED
}
