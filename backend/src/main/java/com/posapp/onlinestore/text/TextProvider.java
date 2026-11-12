package com.posapp.onlinestore.text;

/**
 * A way of delivering text messages (SMS or WhatsApp) to a phone: the built-in test sender now, an SMS company or the WhatsApp Business platform later.
 * A real provider is one more class implementing this, marked {@code @Component}; the core (limits, consent, logging, phone numbers) is shared.
 * See docs/online-store-text-providers.md.
 */
public interface TextProvider {

    /** Short code kept in the shop settings, e.g. SIMULATED. Upper case. */
    String code();

    /** A name for the settings screen. */
    String label();

    /** False when it cannot send right now (switched off, or not set up). */
    boolean available();

    /** True when the provider can deliver on this channel: SMS or WHATSAPP. */
    boolean supports(String channel);

    /**
     * Sends one message. Must not throw for a message that was refused or could not be delivered: it answers {@link Result#failed}.
     * Called while a customer waits, so it should give up within a few seconds.
     *
     * @param to the number in international form with a leading +, e.g. +250788123456
     */
    Result send(String channel, String to, String body);

    /** ok = handed to the provider; reference = the provider's own id for the message (to find it later); error = why not, in English, short. */
    record Result(boolean ok, String reference, String error) {
        public static Result sent(String reference) {
            return new Result(true, reference, null);
        }

        public static Result failed(String error) {
            return new Result(false, null, error);
        }
    }
}
