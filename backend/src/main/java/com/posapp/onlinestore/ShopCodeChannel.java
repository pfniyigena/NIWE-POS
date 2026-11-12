package com.posapp.onlinestore;

/**
 * A way of getting a sign-in code to a customer. E-mail, and SMS / WhatsApp when the shop has texting on.
 */
public interface ShopCodeChannel {

    /** EMAIL or SMS. */
    String code();

    /** False when it cannot send right now (e-mail: no mail server set up; SMS: texting is off). */
    boolean available();

    /** Sends the code, in the customer's language ("fr" or anything else = English). Throws when it could not be sent. */
    void send(String to, String loginCode, String language);

    /** Sends the code that proves a phone number the customer saved on an account. Defaults to the sign-in message. */
    default void sendConfirmation(String to, String code, String language) {
        send(to, code, language);
    }
}
