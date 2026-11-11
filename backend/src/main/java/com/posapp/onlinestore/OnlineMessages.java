package com.posapp.onlinestore;

import com.posapp.email.MailService;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** The e-mails a customer gets about an order. Never blocks or fails an order: a missing address or mail setup just means no mail. */
final class OnlineMessages {

    private static final Logger log = LoggerFactory.getLogger(OnlineMessages.class);

    private OnlineMessages() {
    }

    static void send(MailService mailService, StoreSettingsService storeSettingsService, OnlineOrder o, String subject, String text) {
        if (o.getCustomerEmail() == null || o.getCustomerEmail().isBlank() || !mailService.isConfigured()) return;
        try {
            String shop = storeSettingsService.getSettingsEntity().getBusinessName();
            String body = "<p>" + escape(text) + "</p>" + (shop == null || shop.isBlank() ? "" : "<p>" + escape(shop) + "</p>");
            mailService.send(o.getCustomerEmail(), subject, body, List.of());
        } catch (RuntimeException e) {
            log.warn("Could not e-mail online order {}: {}", o.getCode(), e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
