package com.posapp.subscription;

/** What happened to a subscription; one row of its history. */
public enum SubscriptionEventKind {
    CREATED, EDITED, PLAN_CHANGED, BILLED, SKIPPED, PAUSED, RESUMED, CANCEL_SCHEDULED, CANCEL_UNDONE, CANCELLED, RENEWED, ENDED, REMINDER
}
