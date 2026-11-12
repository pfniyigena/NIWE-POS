package com.posapp.onlinestore;

import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.settings.StoreSettingsService;
import org.springframework.stereotype.Component;

import java.util.List;

/** Sends the sign-in code by e-mail, in the customer's language. */
@Component
public class EmailShopCodeChannel implements ShopCodeChannel {

    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;

    public EmailShopCodeChannel(MailService mailService, StoreSettingsService storeSettingsService) {
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
    }

    @Override
    public String code() {
        return "EMAIL";
    }

    @Override
    public boolean available() {
        return mailService.isConfigured();
    }

    @Override
    public void send(String to, String loginCode, String language) {
        String shop = storeSettingsService.getSettingsEntity().getBusinessName();
        String subject = I18n.tIn(language, "Your sign-in code: {0}", loginCode);
        String text = I18n.tIn(language, "Your sign-in code is {0}. It works for 10 minutes. If you did not ask for it, ignore this message.", loginCode);
        String body = "<p>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</p>"
                + (shop == null || shop.isBlank() ? "" : "<p>" + shop.replace("&", "&amp;").replace("<", "&lt;") + "</p>");
        mailService.send(to, subject, body, List.of());
    }
}
