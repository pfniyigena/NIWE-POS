package com.posapp.onlinestore.text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The SMS sender that is always there, used for the messages that must reach the customer whatever the shop settings say (the delivery / pick-up codes),
 * when no SMS provider has been chosen in the shop settings.
 * <p>
 * <b>This is the one place to plug the real SMS company in.</b> For now nothing leaves the server: {@link #deliver} answers false, and the text is kept in the
 * shop's text log as "Waiting for an SMS company" so staff can see that a code message was due. When an SMS company is connected, send the text here (or,
 * better, add a {@link TextProvider} for it and choose it in the shop settings: it then takes over from this class) and answer true when it was handed over.
 */
@Component
public class BuiltInSmsSender {

    private static final Logger log = LoggerFactory.getLogger(BuiltInSmsSender.class);

    /** Code kept in the text log as the provider of these messages. */
    public static final String CODE = "BUILTIN";

    /**
     * Hands one SMS to the SMS company. Must not throw. The body can hold a secret (a delivery code): never write it to a log.
     *
     * @param to the number in international form with a leading +, e.g. +250788123456
     * @return true when the message was really handed over; false when it was only kept (no SMS company connected yet)
     */
    public boolean deliver(String to, String body) {
        // TODO real SMS company: send `body` to `to` here and return true when accepted.
        log.info("SMS to {} kept in the text log (no SMS company is connected yet), {} characters", to, body == null ? 0 : body.length());
        return false;
    }
}
