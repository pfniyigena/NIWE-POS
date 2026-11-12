package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.settings.StoreSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shop's newsletter list (v1.35.0). An address is only a subscriber once its owner opens the link sent to it (double opt-in), so nobody can be signed
 * up by someone else. Asking again never tells whether an address is already on the list. This version collects and lists addresses; sending a newsletter is
 * done outside for now.
 */
@Service
public class NewsletterService {

    private static final long HOUR = 3_600_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NewsletterSubscriberRepository subscribers;
    private final OnlineStoreService storeService;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    private final String frontendUrl;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public NewsletterService(NewsletterSubscriberRepository subscribers, OnlineStoreService storeService, MailService mailService,
                             StoreSettingsService storeSettingsService, AuditLogService auditLogService,
                             @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.subscribers = subscribers;
        this.storeService = storeService;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.trim().replaceAll("/+$", "");
    }

    private void throttle(String ip, int perHour) {
        long now = System.currentTimeMillis();
        synchronized (byIp) {
            if (byIp.size() > 5000) byIp.values().removeIf(d -> d.isEmpty() || now - d.peekLast() > HOUR);
            Deque<Long> d = byIp.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
            while (!d.isEmpty() && now - d.peekFirst() > HOUR) d.pollFirst();
            if (d.size() >= perHour) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, I18n.t("Too many requests from your connection. Please try again later."));
            d.addLast(now);
        }
    }

    private static String newToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------ the visitor

    /**
     * Asks for the newsletter. The answer is the same whether or not the address is already known: it only says a confirmation e-mail was sent if needed.
     * A robot that fills the hidden field is told "ok" and ignored.
     */
    @Transactional
    public void subscribe(NewsletterRequest in, String ip) {
        OnlineStoreSettings s = storeService.settings();
        if (!s.isEnabled()) throw ApiException.notFound(I18n.t("This shop is not available."));
        throttle("n:" + ip, 6);
        if (in.website() != null && !in.website().isBlank()) return;
        String email = in.email() == null ? "" : in.email().trim().toLowerCase(Locale.ROOT);
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || email.length() > 160) throw ApiException.badRequest(I18n.t("Enter a valid e-mail address."));
        if (!mailService.isConfigured()) throw ApiException.badRequest(I18n.t("The newsletter is not available right now."));
        String language = "fr".equalsIgnoreCase(in.language()) ? "fr" : "en";
        NewsletterSubscriber n = subscribers.findByEmail(email).orElse(null);
        if (n != null && "CONFIRMED".equals(n.getStatus())) return;
        Instant now = Instant.now();
        if (n != null && n.getConfirmSentAt() != null && n.getConfirmSentAt().isAfter(now.minus(Duration.ofMinutes(10)))) return;
        if (n == null) {
            n = new NewsletterSubscriber();
            n.setEmail(email);
            n.setUnsubscribeToken(newToken());
        }
        n.setLanguage(language);
        n.setStatus("PENDING");
        n.setUnsubscribedAt(null);
        n.setSourceIp(ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip);
        String token = newToken();
        n.setConfirmHash(hash(token));
        n.setConfirmSentAt(now);
        subscribers.save(n);
        sendConfirmation(n, token, s);
    }

    private void sendConfirmation(NewsletterSubscriber n, String token, OnlineStoreSettings s) {
        String shop = s.getShopName() != null && !s.getShopName().isBlank() ? s.getShopName() : storeSettingsService.getSettingsEntity().getBusinessName();
        String link = frontendUrl + "/shop/newsletter/confirm/" + token;
        String subject = I18n.tIn(n.getLanguage(), "Confirm your subscription to {0}", shop == null ? "" : shop);
        String text = I18n.tIn(n.getLanguage(), "Please confirm that you want to receive our newsletter by opening this link. If you did not ask for it, ignore this message and nothing will happen.");
        String body = "<p>" + escape(text) + "</p><p><a href=\"" + escape(link) + "\">" + escape(link) + "</a></p>" + (shop == null || shop.isBlank() ? "" : "<p>" + escape(shop) + "</p>");
        try {
            mailService.send(n.getEmail(), subject, body, List.of());
        } catch (RuntimeException e) {
            throw ApiException.badRequest(I18n.t("The confirmation e-mail could not be sent. Please try again later."));
        }
    }

    /** The visitor opened the link of the confirmation e-mail. Returns the token that lets them unsubscribe later. */
    @Transactional
    public String confirm(String token, String ip) {
        throttle("nc:" + ip, 30);
        NewsletterSubscriber n = token == null || token.isBlank() ? null : subscribers.findByConfirmHash(hash(token.trim())).orElse(null);
        if (n == null) throw ApiException.badRequest(I18n.t("This link is not valid, or it was already used."));
        n.setStatus("CONFIRMED");
        n.setConfirmHash(null);
        n.setConfirmedAt(Instant.now());
        n.setUnsubscribedAt(null);
        subscribers.save(n);
        return n.getUnsubscribeToken();
    }

    @Transactional
    public void unsubscribe(String token, String ip) {
        throttle("nu:" + ip, 30);
        NewsletterSubscriber n = token == null || token.isBlank() ? null : subscribers.findByUnsubscribeToken(token.trim()).orElse(null);
        if (n == null) throw ApiException.badRequest(I18n.t("This link is not valid, or it was already used."));
        n.setStatus("UNSUBSCRIBED");
        n.setConfirmHash(null);
        n.setUnsubscribedAt(Instant.now());
        subscribers.save(n);
    }

    // ------------------------------------------------------------------ staff

    @Transactional(readOnly = true)
    public List<NewsletterDto> list(String status, String q) {
        List<NewsletterSubscriber> all = status == null || status.isBlank() || status.equals("ALL") ? subscribers.findAllByOrderByIdDesc()
                : subscribers.findByStatusOrderByIdDesc(status.trim().toUpperCase(Locale.ROOT));
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return all.stream().filter(n -> needle.isEmpty() || n.getEmail().contains(needle)).map(NewsletterService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        return Map.of("CONFIRMED", subscribers.countByStatus("CONFIRMED"), "PENDING", subscribers.countByStatus("PENDING"),
                "UNSUBSCRIBED", subscribers.countByStatus("UNSUBSCRIBED"));
    }

    /** The confirmed addresses, one per line with language and date, ready to open in a spreadsheet. */
    @Transactional(readOnly = true)
    public String csv() {
        StringBuilder sb = new StringBuilder("email,language,confirmed_at\r\n");
        for (NewsletterSubscriber n : subscribers.findByStatusOrderByIdDesc("CONFIRMED")) {
            sb.append(cell(n.getEmail())).append(',').append(n.getLanguage()).append(',').append(n.getConfirmedAt() == null ? "" : n.getConfirmedAt().toString()).append("\r\n");
        }
        auditLogService.record("NEWSLETTER_EXPORTED", "Exported the confirmed newsletter addresses");
        return sb.toString();
    }

    /** A cell that starts with = + - or @ is made harmless for a spreadsheet. */
    private static String cell(String v) {
        String x = v == null ? "" : v;
        if (!x.isEmpty() && "=+-@".indexOf(x.charAt(0)) >= 0) x = "'" + x;
        return "\"" + x.replace("\"", "\"\"") + "\"";
    }

    @Transactional
    public void delete(Long id) {
        NewsletterSubscriber n = subscribers.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Subscriber not found")));
        subscribers.delete(n);
        auditLogService.record("NEWSLETTER_SUBSCRIBER_DELETED", "Removed a newsletter address", "NewsletterSubscriber", id);
    }

    private static NewsletterDto toDto(NewsletterSubscriber n) {
        return new NewsletterDto(n.getId(), n.getEmail(), n.getLanguage(), n.getStatus(), n.getCreatedAt(), n.getConfirmedAt(), n.getUnsubscribedAt());
    }
}
