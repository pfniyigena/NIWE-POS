package com.posapp.helpdesk;

/**
 * Fourier: "Add Help Desk module ... from creation through resolution." A ticket's lifecycle —
 * deliberately simple explicit transitions (see {@code HelpDeskTicketService.start/resolve/close/
 * reopen}), the same "a handful of named actions, not a generic status setter" convention as
 * {@code SubscriptionStatus} (pause/resume/cancel) rather than a free-form state machine.
 *
 * <p>{@link #OPEN} → {@link #IN_PROGRESS} → {@link #RESOLVED} → {@link #CLOSED} is the normal
 * path, but {@link #CLOSED} is also reachable directly from {@link #OPEN}/{@link #IN_PROGRESS}
 * (e.g. a duplicate or spam ticket closed without ever being "resolved") and {@link #RESOLVED}/
 * {@link #CLOSED} can both go back to {@link #OPEN} via {@code reopen} if the requester reports
 * the issue isn't actually fixed.
 */
public enum HelpDeskStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
