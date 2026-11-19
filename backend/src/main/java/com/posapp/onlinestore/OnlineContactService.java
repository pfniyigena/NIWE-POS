package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The contact form of the shop (v1.51.0). A message is saved for staff (Online Store > Messages), the shop is told by e-mail, and a visitor who gave an
 * e-mail address gets a short confirmation with a copy. The visitor must give an e-mail or a phone number so the shop can answer. A robot that fills the
 * hidden field is told "ok" and ignored; one connection may send 5 messages an hour.
 */
@Service
public class OnlineContactService {

    private static final Logger log = LoggerFactory.getLogger(OnlineContactService.class);
    private static final long HOUR = 3_600_000L;

    private final OnlineContactMessageRepository messages;
    private final OnlineStoreService storeService;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public OnlineContactService(OnlineContactMessageRepository messages, OnlineStoreService storeService, MailService mailService,
                                StoreSettingsService storeSettingsService, AuditLogService auditLogService) {
        this.messages = messages;
        this.storeService = storeService;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
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

    private static String clean(String v, int max) {
        String x = v == null ? "" : v.trim();
        return x.length() > max ? x.substring(0, max) : x;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("\n", "<br>");
    }

    // ------------------------------------------------------------------ the visitor

    @Transactional
    public void send(ContactRequest in, String ip) {
        OnlineStoreSettings s = storeService.openSettings();
        throttle("c:" + ip, 5);
        if (in.website() != null && !in.website().isBlank()) return;
        String name = clean(in.name(), 80);
        String email = clean(in.email(), 160).toLowerCase(Locale.ROOT);
        String phone = clean(in.phone(), 40);
        String message = clean(in.message(), 2000);
        if (name.isEmpty()) throw ApiException.badRequest(I18n.t("Enter your name."));
        if (email.isEmpty() && phone.replaceAll("[^0-9]", "").length() < 6) throw ApiException.badRequest(I18n.t("Give an e-mail address or a phone number so we can answer you."));
        if (!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest(I18n.t("Enter a valid e-mail address."));
        if (message.length() < 5) throw ApiException.badRequest(I18n.t("Write your message."));
        String language = "fr".equalsIgnoreCase(in.language()) ? "fr" : "en";
        OnlineContactMessage m = new OnlineContactMessage();
        m.setName(name);
        m.setEmail(email.isEmpty() ? null : email);
        m.setPhone(phone.isEmpty() ? null : phone);
        String subject = clean(in.subject(), 120);
        m.setSubject(subject.isEmpty() ? null : subject);
        m.setMessage(message);
        String code = clean(in.orderCode(), 20).toUpperCase(Locale.ROOT);
        m.setOrderCode(code.isEmpty() ? null : code);
        m.setLanguage(language);
        m.setSourceIp(ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip);
        messages.save(m);
        tellShop(s, m);
        confirm(s, m);
    }

    private void tellShop(OnlineStoreSettings s, OnlineContactMessage m) {
        StringBuilder t = new StringBuilder();
        t.append(m.getName());
        if (m.getPhone() != null) t.append(" · ").append(m.getPhone());
        if (m.getEmail() != null) t.append(" · ").append(m.getEmail());
        if (m.getOrderCode() != null) t.append(" · ").append(I18n.t("Order {0}", m.getOrderCode()));
        t.append(": ");
        if (m.getSubject() != null) t.append(m.getSubject()).append(" - ");
        t.append(m.getMessage());
        OnlineMessages.notifyShopNote(mailService, storeSettingsService, s, I18n.t("New message from {0}", m.getName()), t.toString());
    }

    /** A short confirmation with a copy of the message, in the visitor's language. Never fails the request. */
    private void confirm(OnlineStoreSettings s, OnlineContactMessage m) {
        if (m.getEmail() == null) return;
        try {
            if (!mailService.isConfigured()) return;
            String shop = s.getShopName() != null && !s.getShopName().isBlank() ? s.getShopName() : storeSettingsService.getSettingsEntity().getBusinessName();
            String intro = I18n.tIn(m.getLanguage(), "We received your message and will answer as soon as we can. Here is a copy.");
            String body = "<p>" + escape(intro) + "</p><blockquote style=\"margin:0 0 1em; padding-left:0.8em; border-left:3px solid #ccc;\">"
                    + (m.getSubject() == null ? "" : "<strong>" + escape(m.getSubject()) + "</strong><br>") + escape(m.getMessage()) + "</blockquote>"
                    + (shop == null || shop.isBlank() ? "" : "<p>" + escape(shop) + "</p>");
            mailService.send(m.getEmail(), I18n.tIn(m.getLanguage(), "We received your message") + (shop == null || shop.isBlank() ? "" : " - " + shop), body, List.of());
        } catch (RuntimeException e) {
            log.warn("Could not send the contact confirmation: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ staff

    @Transactional(readOnly = true)
    public List<ContactMessageDto> list(String status, String q) {
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        List<OnlineContactMessage> all = st.equals("NEW") || st.equals("ANSWERED") ? messages.findByStatusOrderByIdDesc(st) : messages.findAllByOrderByIdDesc();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return all.stream().filter(m -> needle.isEmpty() || (m.getName() + " " + nz(m.getEmail()) + " " + nz(m.getPhone()) + " " + nz(m.getSubject()) + " " + m.getMessage()
                + " " + nz(m.getOrderCode())).toLowerCase(Locale.ROOT).contains(needle)).map(OnlineContactService::dto).toList();
    }

    /** NEW and ANSWERED counts (the New one is the number on the Messages tab and the menu). */
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        return Map.of("messagesNew", messages.countByStatus("NEW"), "messagesAnswered", messages.countByStatus("ANSWERED"));
    }

    @Transactional
    public ContactMessageDto answered(Long id, String note) {
        OnlineContactMessage m = find(id);
        m.setStatus("ANSWERED");
        m.setNote(note == null || note.isBlank() ? m.getNote() : clean(note, 500));
        m.setHandledBy(who());
        m.setHandledAt(Instant.now());
        return dto(messages.save(m));
    }

    @Transactional
    public ContactMessageDto reopen(Long id) {
        OnlineContactMessage m = find(id);
        m.setStatus("NEW");
        return dto(messages.save(m));
    }

    @Transactional
    public void delete(Long id) {
        messages.delete(find(id));
        auditLogService.record("SHOP_MESSAGE_DELETED", "Deleted a contact message from the shop");
    }

    private OnlineContactMessage find(Long id) {
        return messages.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static String who() {
        org.springframework.security.core.Authentication a = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
    }

    private static ContactMessageDto dto(OnlineContactMessage m) {
        return new ContactMessageDto(m.getId(), m.getName(), m.getEmail(), m.getPhone(), m.getSubject(), m.getMessage(), m.getOrderCode(), m.getLanguage(),
                m.getStatus(), m.getNote(), m.getHandledBy(), m.getHandledAt(), m.getCreatedAt());
    }
}
