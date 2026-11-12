package com.posapp.onlinestore;

/**
 * A way of getting a sign-in code to a customer. Only e-mail exists today; an SMS or WhatsApp channel is one more class implementing this.
 */
public interface ShopCodeChannel {

    /** EMAIL. */
    String code();

    /** False when it cannot send right now (for e-mail: no mail server set up). */
    boolean available();

    /** Sends the code, in the customer's language ("fr" or anything else = English). Throws when it could not be sent. */
    void send(String to, String loginCode, String language);
}
