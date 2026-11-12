package com.posapp.onlinestore.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A company that takes the customer's money for the shop (a mobile money operator, a card gateway). The shop talks to every provider through this
 * one interface, so adding a real provider means writing one class that implements it and registering it as a Spring component; nothing else changes.
 * Secrets (keys, webhook secrets) are read from the environment or properties by the provider itself and are never stored in the database.
 */
public interface PaymentProvider {

    /** The code the shop settings store, e.g. SIMULATED. Upper case, at most 30 characters. */
    String code();

    /** The name staff see in the settings. */
    String label();

    /** False when the provider is not configured (no keys, switched off). Only an available provider can be chosen and used. */
    boolean available();

    /** The ways the customer can pay with this provider: MOBILE_MONEY and/or CARD. */
    List<String> methods();

    /** Starts a payment. A mobile money payment usually asks the customer to approve on the phone; a card payment gives a page to redirect to. */
    Initiated initiate(PaymentRequest request);

    /** Asks the provider what became of a payment (used when the webhook did not arrive). Must not throw for an unknown reference: answer PENDING. */
    StatusResult status(String reference);

    /** Gives the money back. A provider that cannot refund by itself answers ok = false and staff refund by hand and enter the reference. */
    RefundResult refund(String reference, BigDecimal amount, String reason);

    /** True when the call really came from the provider. rawBody is exactly the bytes received; headers have lower-case names. */
    boolean verifySignature(String rawBody, Map<String, String> headers);

    /** Reads the webhook body. Only called after verifySignature said true. */
    WebhookEvent parseWebhook(String rawBody);

    enum State { PENDING, PAID, FAILED }

    /** reference is ours (what the provider must send back); returnUrl is where the customer lands after a card page. */
    record PaymentRequest(String reference, BigDecimal amount, String currency, String method, String phone, String description,
                          String customerName, String customerEmail, String returnUrl) {
    }

    /** redirectUrl is empty for a payment approved on the phone. message is shown to the customer. */
    record Initiated(String providerReference, String redirectUrl, String message) {
    }

    record StatusResult(State state, BigDecimal amount, String currency, String providerReference, String reason) {
    }

    record RefundResult(boolean ok, String refundReference, String message) {
    }

    record WebhookEvent(String reference, State state, BigDecimal amount, String currency, String providerReference, String reason) {
    }
}
