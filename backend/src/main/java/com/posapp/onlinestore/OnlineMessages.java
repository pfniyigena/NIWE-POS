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

    /** Set once at start-up by {@link OnlineTextService}. Null (no texts at all) in anything that does not start it. */
    private static volatile OnlineTextService texts;

    static void texts(OnlineTextService service) {
        texts = service;
    }

    private OnlineMessages() {
    }

    /** A text written in English with {0}, {1} values; it is put into the customer's language only when the mail is sent. */
    record Msg(String english, Object[] args, String suffix) {
        /** The same text followed by something that needs no translation (an address, say). */
        Msg plus(String more) {
            return new Msg(english, args, suffix + (more == null ? "" : more));
        }
    }

    static Msg m(String english, Object... args) {
        return new Msg(english, args, "");
    }

    /** The mail goes in the language the customer chose in the shop (stored on the order), not the business language. */
    static void send(MailService mailService, StoreSettingsService storeSettingsService, OnlineOrder o, Msg subjectMsg, Msg textMsg) {
        textOrder(o, textMsg);
        if (o.getCustomerEmail() == null || o.getCustomerEmail().isBlank() || !mailService.isConfigured()) return;
        try {
            String subject = I18n.tIn(o.getLanguage(), subjectMsg.english(), subjectMsg.args());
            String text = I18n.tIn(o.getLanguage(), textMsg.english(), textMsg.args()) + textMsg.suffix();
            String shop = storeSettingsService.getSettingsEntity().getBusinessName();
            String body = "<p>" + escape(text) + "</p>" + (shop == null || shop.isBlank() ? "" : "<p>" + escape(shop) + "</p>");
            mailService.send(o.getCustomerEmail(), subject, body, List.of());
        } catch (RuntimeException e) {
            log.warn("Could not e-mail online order {}: {}", o.getCode(), e.getMessage());
        }
    }

    /** The same update by text, only when the customer ticked the box at checkout and texting is on. A text never blocks or fails the order. */
    private static void textOrder(OnlineOrder o, Msg textMsg) {
        OnlineTextService t = texts;
        if (t == null || !o.isTextOptIn()) return;
        try {
            t.orderUpdate(o, I18n.tIn(o.getLanguage(), textMsg.english(), textMsg.args()) + textMsg.suffix());
        } catch (RuntimeException e) {
            log.warn("Could not text online order {}: {}", o.getCode(), e.getMessage());
        }
    }

    private static void textOwner(OnlineStoreSettings settings, String text) {
        OnlineTextService t = texts;
        if (t == null) return;
        try {
            t.owner(settings, text);
        } catch (RuntimeException e) {
            log.warn("Could not text the shop owner: {}", e.getMessage());
        }
    }

    /**
     * Tells the shop that a customer placed an order: to the e-mail set in the Online Store settings, or the business e-mail when that is empty.
     * Like the customer's mails it never blocks or fails the order.
     */
    static void notifyShop(MailService mailService, StoreSettingsService storeSettingsService, OnlineStoreSettings settings, OnlineOrder o) {
        textOwner(settings, I18n.t("New online order {0} from {1} ({2}), total {3}. Open Online Store > Online orders.", o.getCode(), o.getCustomerName(), o.getCustomerPhone(), OnlineStoreService.money(o.getTotal())));
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
            b.append("<p><strong>Total: ").append(escape(OnlineStoreService.money(o.getTotal()))).append("</strong>");
            if ("PAID".equals(o.getPaymentStatus())) b.append(" — ").append(escape(I18n.t("already paid online"))).append(" (").append(escape(String.valueOf(o.getPaymentChannel()))).append(", ").append(escape(String.valueOf(o.getPaymentReference()))).append(")");
            b.append("</p>");
            if (o.getNotes() != null && !o.getNotes().isBlank()) b.append("<p>Note: ").append(escape(o.getNotes())).append("</p>");
            b.append("<p>").append(escape(I18n.t("Open Online Store > Online orders to accept or reject it."))).append("</p>");
            mailService.send(to, I18n.t("New online order {0}", o.getCode()), b.toString(), List.of());
        } catch (RuntimeException e) {
            log.warn("Could not e-mail the shop about online order {}: {}", o.getCode(), e.getMessage());
        }
    }

    /** A short notice to the shop's e-mail (or the business e-mail) and to the owner's phone, in the business language. Never blocks or fails what triggered it. */
    static void notifyShopNote(MailService mailService, StoreSettingsService storeSettingsService, OnlineStoreSettings settings, String subject, String text) {
        textOwner(settings, text);
        try {
            String to = settings.getNotifyEmail();
            if (to == null || to.isBlank()) to = storeSettingsService.getSettingsEntity().getEmail();
            if (to == null || to.isBlank() || !mailService.isConfigured()) return;
            mailService.send(to, subject, "<p>" + escape(text) + "</p>", List.of());
        } catch (RuntimeException e) {
            log.warn("Could not e-mail the shop: {}", e.getMessage());
        }
    }

    /** The shop is told that these orders are still waiting for an answer. */
    static void alertShop(MailService mailService, StoreSettingsService storeSettingsService, OnlineStoreSettings settings, List<OnlineOrder> orders,
                          int minutes, java.time.Instant now) {
        textOwner(settings, I18n.t("{0} online order(s) have waited more than {1} minutes for an answer.", orders.size(), minutes));
        try {
            String to = settings.getNotifyEmail();
            if (to == null || to.isBlank()) to = storeSettingsService.getSettingsEntity().getEmail();
            if (to == null || to.isBlank() || !mailService.isConfigured()) return;
            StringBuilder b = new StringBuilder();
            b.append("<p><strong>").append(escape(I18n.t("{0} online order(s) have waited more than {1} minutes for an answer.", orders.size(), minutes))).append("</strong></p><ul>");
            for (OnlineOrder o : orders) {
                long waited = java.time.Duration.between(o.getCreatedAt(), now).toMinutes();
                b.append("<li>").append(escape(o.getCode())).append(" — ").append(escape(o.getCustomerName())).append(", ").append(escape(o.getCustomerPhone()))
                        .append(", ").append(escape(OnlineStoreService.money(o.getTotal()))).append(" (").append(waited).append(" min)</li>");
            }
            b.append("</ul><p>").append(escape(I18n.t("Open Online Store > Online orders to accept or reject it."))).append("</p>");
            mailService.send(to, I18n.t("Online orders are waiting for an answer"), b.toString(), List.of());
        } catch (RuntimeException e) {
            log.warn("Could not e-mail the shop about waiting online orders: {}", e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
