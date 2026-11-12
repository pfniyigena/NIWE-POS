package com.posapp.onlinestore;

import com.posapp.i18n.I18n;
import com.posapp.onlinestore.text.TextProvider;
import com.posapp.settings.StoreSettingsService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Text messages (SMS or WhatsApp) for the online shop. The provider carries the message; everything else is here and the same for every provider:
 * phone numbers are put in international form, a daily limit caps the cost (and any abuse), every message is written to the shop's text log
 * (also the ones that failed or were skipped), and a failure never breaks the order or the sign-in that caused it.
 * <p>
 * Consent is not decided here: order updates are only handed to {@link #orderUpdate} for customers who ticked the box (see {@code OnlineOrder.textOptIn}).
 * The message is sent while the customer's request waits (like the e-mail), so a slow provider slows that request; a provider must time out within seconds.
 */
@Service
public class OnlineTextService {

    private static final Logger log = LoggerFactory.getLogger(OnlineTextService.class);
    static final int MAX_BODY = 320;
    static final Duration KEEP = Duration.ofDays(90);

    private final OnlineTextMessageRepository messages;
    private final OnlineStoreSettingsRepository settingsRepository;
    private final List<TextProvider> providers;
    private final StoreSettingsService storeSettingsService;
    private final TransactionTemplate ownTransaction;

    public OnlineTextService(OnlineTextMessageRepository messages, OnlineStoreSettingsRepository settingsRepository, List<TextProvider> providers,
                             StoreSettingsService storeSettingsService, PlatformTransactionManager transactionManager) {
        this.messages = messages;
        this.settingsRepository = settingsRepository;
        this.providers = providers;
        this.storeSettingsService = storeSettingsService;
        // The log row is written in its own transaction, so it survives when the sign-in or order that caused the text is rolled back.
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** What a send came to: SENT, FAILED or SKIPPED (not valid, over the limit). */
    public record Outcome(String status, String error) {
        public boolean sent() {
            return "SENT".equals(status);
        }
    }

    @PostConstruct
    void hook() {
        // The order e-mails are written as static helpers; this is how they reach the text service (see OnlineMessages).
        OnlineMessages.texts(this);
    }

    // ------------------------------------------------------------------ what is on

    private OnlineStoreSettings settings() {
        return settingsRepository.findAll().stream().findFirst().orElse(null);
    }

    public List<TextProvider> providers() {
        return providers;
    }

    TextProvider find(String code) {
        if (code == null) return null;
        return providers.stream().filter(p -> p.code().equalsIgnoreCase(code.trim())).findFirst().orElse(null);
    }

    private TextProvider provider(OnlineStoreSettings s) {
        if (s == null || s.getTextProvider() == null || s.getTextProvider().isBlank()) return null;
        TextProvider p = find(s.getTextProvider());
        return p != null && p.available() && p.supports(channelOf(s)) ? p : null;
    }

    private static String channelOf(OnlineStoreSettings s) {
        return "WHATSAPP".equals(s.getTextChannel()) ? "WHATSAPP" : "SMS";
    }

    /** True when texts can be sent now: a provider is chosen, it is available and it carries the chosen channel. */
    public boolean enabled() {
        return provider(settings()) != null;
    }

    /** SMS or WHATSAPP when texts can be sent now, else null. This is what the public shop shows next to the tick box. */
    public String channel() {
        OnlineStoreSettings s = settings();
        return provider(s) == null ? null : channelOf(s);
    }

    // ------------------------------------------------------------------ phone numbers

    /**
     * The number in international form (+250788123456), or null when it is not a phone number. A number starting with 0 gets the country code of the shop;
     * one starting with + or 00 is taken as it is.
     */
    public static String toE164(String phone, String countryCode) {
        if (phone == null) return null;
        String p = phone.trim();
        if (p.isEmpty() || p.replaceAll("[0-9+\\-() .]", "").length() > 0 || p.indexOf('+', 1) >= 0) return null;
        String digits = p.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        String cc = countryCode == null ? "" : countryCode.replaceAll("\\D", "");
        String out;
        if (p.startsWith("+")) out = digits;
        else if (p.startsWith("00")) out = digits.substring(2);
        else if (cc.isEmpty()) return null;
        else if (digits.startsWith("0")) out = cc + digits.substring(1);
        else if (digits.startsWith(cc) && digits.length() >= cc.length() + 7) out = digits;
        else out = cc + digits;
        return out.length() < 8 || out.length() > 15 ? null : "+" + out;
    }

    /** {@link #toE164} with the shop's country code. */
    public String toE164(String phone) {
        OnlineStoreSettings s = settings();
        return toE164(phone, s == null ? "250" : s.getTextCountryCode());
    }

    // ------------------------------------------------------------------ sending

    private String shopName() {
        OnlineStoreSettings s = settings();
        String name = s == null ? null : s.getShopName();
        if (name == null || name.isBlank()) name = storeSettingsService.getSettingsEntity().getBusinessName();
        return name == null ? "" : name.trim();
    }

    /** The shop name in front, so the customer knows who is writing. */
    public String withShop(String text) {
        String shop = shopName();
        return shop.isEmpty() ? text : shop + ": " + text;
    }

    private static String cut(String s) {
        return s.length() <= MAX_BODY ? s : s.substring(0, MAX_BODY - 3) + "...";
    }

    /**
     * Sends one text and logs it. Never throws: a provider that fails, a bad number or the daily limit is an {@link Outcome} (and a log row).
     * Answers SKIPPED without a log row when texting is off.
     *
     * @param logBody what the log shows instead of the text (a sign-in code is never kept), or null to log the text
     */
    public Outcome send(String purpose, String phone, String body, String logBody, Long orderId) {
        OnlineStoreSettings s = settings();
        TextProvider p = provider(s);
        if (p == null) return new Outcome("SKIPPED", "Text messages are off");
        String channel = channelOf(s);
        String text = cut(body == null ? "" : body);
        String shown = cut(logBody == null ? text : logBody);
        String to = toE164(phone, s.getTextCountryCode());
        if (to == null) {
            record(channel, phone == null ? "" : phone, purpose, shown, "SKIPPED", p.code(), null, "Not a phone number", orderId);
            return new Outcome("SKIPPED", "Not a phone number");
        }
        int limit = s.getTextDailyLimit();
        if (limit > 0 && messages.countByStatusAndCreatedAtAfter("SENT", Instant.now().minus(Duration.ofHours(24))) >= limit) {
            record(channel, to, purpose, shown, "SKIPPED", p.code(), null, "The daily limit of " + limit + " messages is reached", orderId);
            log.warn("The daily limit of {} text messages is reached: a '{}' text was not sent", limit, purpose);
            return new Outcome("SKIPPED", "The daily limit is reached");
        }
        TextProvider.Result r;
        try {
            r = p.send(channel, to, text);
        } catch (RuntimeException e) {
            r = TextProvider.Result.failed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        if (r == null) r = TextProvider.Result.failed("No answer from the provider");
        String error = r.ok() ? null : (r.error() == null || r.error().isBlank() ? "Not sent" : r.error());
        record(channel, to, purpose, shown, r.ok() ? "SENT" : "FAILED", p.code(), r.reference(), error, orderId);
        if (!r.ok()) log.warn("A '{}' text could not be sent through {}: {}", purpose, p.code(), error);
        return new Outcome(r.ok() ? "SENT" : "FAILED", error);
    }

    private void record(String channel, String to, String purpose, String body, String status, String provider, String ref, String error, Long orderId) {
        try {
            ownTransaction.executeWithoutResult(t -> {
                OnlineTextMessage m = new OnlineTextMessage();
                m.setChannel(channel);
                m.setToPhone(to.length() > 24 ? to.substring(0, 24) : to);
                m.setPurpose(purpose);
                m.setBody(body);
                m.setStatus(status);
                m.setProvider(provider);
                m.setProviderRef(ref == null ? null : ref.length() > 80 ? ref.substring(0, 80) : ref);
                m.setError(error == null ? null : error.length() > 240 ? error.substring(0, 240) : error);
                m.setOnlineOrderId(orderId);
                messages.save(m);
            });
        } catch (RuntimeException e) {
            log.warn("Could not write the text message log: {}", e.getMessage());
        }
    }

    /** An update about an order, only for a customer who ticked the box at checkout. */
    public void orderUpdate(OnlineOrder o, String text) {
        if (!o.isTextOptIn() || o.getCustomerPhone() == null || o.getCustomerPhone().isBlank()) return;
        send("ORDER", o.getCustomerPhone(), withShop(text), null, o.getId());
    }

    /** The shop owner's own phone (Online Store settings), told about new orders and orders waiting too long. */
    public void owner(OnlineStoreSettings s, String text) {
        if (s == null || s.getNotifyPhone() == null || s.getNotifyPhone().isBlank()) return;
        send("OWNER", s.getNotifyPhone(), withShop(text), null, null);
    }

    // ------------------------------------------------------------------ the log (staff)

    public Page<OnlineTextMessage> history(int page, int size) {
        return messages.findAllByOrderByIdDesc(PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
    }

    /** The staff "send a test message" button. Goes through exactly the same limits and log as every other text. */
    public Outcome test(String phone, String text) {
        String body = text == null || text.isBlank() ? I18n.t("This is a test message from your online shop.") : text.trim();
        return send("TEST", phone, withShop(body), null, null);
    }

    /** Messages older than 90 days are removed. */
    @Transactional
    public int purge(Instant now) {
        return messages.deleteOlderThan(now.minus(KEEP));
    }
}
