package com.posapp.onlinestore;

import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
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

    /**
     * Tells the shop that a customer placed an order: to the e-mail set in the Online Store settings, or the business e-mail when that is empty.
     * Like the customer's mails it never blocks or fails the order.
     */
    static void notifyShop(MailService mailService, StoreSettingsService storeSettingsService, OnlineStoreSettings settings, OnlineOrder o) {
        try {
            String to = settings.getNotifyEmail();
            if (to == null || to.isBlank()) to = storeSettingsService.getSettingsEntity().getEmail();
            if (to == null || to.isBlank() || !mailService.isConfigured()) return;
            StringBuilder b = new StringBuilder();
            b.append("<p><strong>").append(escape(I18n.t("A new online order was placed on the shop."))).append("</strong></p>");
            b.append("<p>").append(escape(I18n.t("Customer"))).append(": ").append(escape(o.getCustomerName())).append("<br>")
                    .append(escape(I18n.t("Phone"))).append(": ").append(escape(o.getCustomerPhone()));
            if (o.getCustomerEmail() != null && !o.getCustomerEmail().isBlank()) b.append("<br>E-mail: ").append(escape(o.getCustomerEmail()));
            b.append("</p><p>").append(escape(o.getDeliveryMethod().equals("DELIVERY") ? I18n.t("Delivery") : I18n.t("Pick-up")));
            if (o.getZoneName() != null && !o.getZoneName().isBlank()) b.append("<br>").append(escape(I18n.t("Delivery area"))).append(": ").append(escape(o.getZoneName()));
            if (o.getCustomerAddress() != null && !o.getCustomerAddress().isBlank()) b.append("<br>").append(escape(I18n.t("Address"))).append(": ").append(escape(o.getCustomerAddress()));
            b.append("</p><ul>");
            for (OnlineOrderLine l : o.getLines()) {
                b.append("<li>").append(l.getQuantity().stripTrailingZeros().toPlainString()).append(" × ").append(escape(l.getName()))
                        .append(" — ").append(escape(OnlineStoreService.money(l.getLineTotal()))).append("</li>");
            }
            b.append("</ul>");
            if (o.getDeliveryFee() != null && o.getDeliveryFee().signum() > 0) {
                b.append("<p>").append(escape(I18n.t("Delivery fee"))).append(": ").append(escape(OnlineStoreService.money(o.getDeliveryFee()))).append("</p>");
            }
            b.append("<p><strong>Total: ").append(escape(OnlineStoreService.money(o.getTotal()))).append("</strong></p>");
            if (o.getNotes() != null && !o.getNotes().isBlank()) b.append("<p>Note: ").append(escape(o.getNotes())).append("</p>");
            b.append("<p>").append(escape(I18n.t("Open Online Store > Online orders to accept or reject it."))).append("</p>");
            mailService.send(to, I18n.t("New online order {0}", o.getCode()), b.toString(), List.of());
        } catch (RuntimeException e) {
            log.warn("Could not e-mail the shop about online order {}: {}", o.getCode(), e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
