package com.posapp.subscription;

/**
 * A {@link Subscription}'s own lifecycle. ACTIVE is the only state {@link
 * SubscriptionService#billDue} ever bills. PAUSED is reversible ({@link
 * SubscriptionService#resume}) — e.g. a customer on vacation; CANCELLED is terminal — same
 * "pause is reversible, cancel is not" distinction this app already draws elsewhere between a
 * temporary hold and a final state (e.g. {@code PaymentMethodEntity.active} toggles freely, but
 * {@code InvoiceStatus.VOID} never goes back to {@code ISSUED}).
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAUSED,
    CANCELLED
}
