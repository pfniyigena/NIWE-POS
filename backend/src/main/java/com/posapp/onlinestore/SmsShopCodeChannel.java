package com.posapp.onlinestore;

import com.posapp.i18n.I18n;
import org.springframework.stereotype.Component;

/**
 * Sends the sign-in code as a text message (SMS or WhatsApp, whichever the shop chose) through the shop's text provider.
 * The text log shows the message without the code.
 */
@Component
public class SmsShopCodeChannel implements ShopCodeChannel {

    private final OnlineTextService texts;

    public SmsShopCodeChannel(OnlineTextService texts) {
        this.texts = texts;
    }

    @Override
    public String code() {
        return "SMS";
    }

    @Override
    public boolean available() {
        return texts.enabled();
    }

    @Override
    public void send(String to, String loginCode, String language) {
        deliver(to, I18n.tIn(language, "Your sign-in code is {0}. It works for 10 minutes. If you did not ask for it, ignore this message.", loginCode),
                I18n.tIn(language, "Your sign-in code is {0}. It works for 10 minutes. If you did not ask for it, ignore this message.", "******"));
    }

    @Override
    public void sendConfirmation(String to, String code, String language) {
        deliver(to, I18n.tIn(language, "Your code to confirm this phone number is {0}. It works for 10 minutes. If you did not ask for it, ignore this message.", code),
                I18n.tIn(language, "Your code to confirm this phone number is {0}. It works for 10 minutes. If you did not ask for it, ignore this message.", "******"));
    }

    private void deliver(String to, String text, String logText) {
        OnlineTextService.Outcome o = texts.send("SIGNIN", to, texts.withShop(text), texts.withShop(logText), null);
        if (!o.sent()) throw new IllegalStateException("The code could not be texted: " + o.error());
    }
}
