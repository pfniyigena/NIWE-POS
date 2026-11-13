package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reminders for abandoned carts (v1.39.0). When the shop has switched the reminders on and a visitor types an e-mail address at checkout, the cart is kept
 * (one per address). The customer then gets at most two e-mails: 1 hour and 3 days after the last change to the cart. There is no discount: the e-mail lists
 * the cart as it is on the shop today (current prices, sold-out items left out) and links back to it. Every e-mail has a link that stops the reminders, and an
 * address that stopped is never reminded again. A cart ends as RECOVERED as soon as an order is placed with that address.
 */
@Service
public class CartReminderService {

    private static final Logger log = LoggerFactory.getLogger(CartReminderService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    static final int MAX_LINES = 30;
    static final int MAX_QUANTITY = 20;
    static final Duration FIRST_AFTER = Duration.ofHours(1);
    static final Duration SECOND_AFTER = Duration.ofDays(3);
    /** A reminder that is this late is not sent: the customer has surely moved on (the server was off, or the feature was just switched on). */
    static final Duration FIRST_TOO_LATE = Duration.ofDays(2);
    static final Duration SECOND_TOO_LATE = Duration.ofDays(10);
    /** An address that was reminded gets its reminders again only after this long, however often its cart changes. */
    static final Duration AGAIN_AFTER = Duration.ofDays(7);
    static final Duration RETRY_AFTER = Duration.ofMinutes(30);
    static final Duration KEEP_FOR = Duration.ofDays(60);
    private static final long HOUR = 3_600_000L;

    private final OnlineCartRepository carts;
    private final OnlineCartLineRepository cartLines;
    private final OnlineListingRepository listings;
    private final OnlineOrderRepository orders;
    private final OnlineStoreService storeService;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate tx;
    private final String frontendUrl;
    private final int batchSize;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public CartReminderService(OnlineCartRepository carts, OnlineCartLineRepository cartLines, OnlineListingRepository listings, OnlineOrderRepository orders,
                               OnlineStoreService storeService, MailService mailService, StoreSettingsService storeSettingsService,
                               AuditLogService auditLogService, PlatformTransactionManager transactionManager,
                               @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
                               @Value("${app.online-store.cart-reminders.batch-size:20}") int batchSize) {
        this.carts = carts;
        this.cartLines = cartLines;
        this.listings = listings;
        this.orders = orders;
        this.storeService = storeService;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
        this.tx = new TransactionTemplate(transactionManager);
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.trim().replaceAll("/+$", "");
        this.batchSize = Math.max(1, batchSize);
    }

    // ------------------------------------------------------------------ the visitor

    /**
     * The visitor's cart, sent while they type their order. Nothing happens (and nothing is said) when the reminders are off, the mail server is not set up,
     * the address does not look right or the cart is empty of anything on sale: the order itself must never be disturbed by this.
     */
    @Transactional
    public void save(CartSave in, String ip) {
        OnlineStoreSettings s = storeService.settings();
        if (!s.isEnabled() || !s.isCartRemindersEnabled() || !mailService.isConfigured()) return;
        if (in.website() != null && !in.website().isBlank()) return;
        throttle(ip);
        String email = in.email() == null ? "" : in.email().trim().toLowerCase(Locale.ROOT);
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || email.length() > 160) return;
        List<PublicLine> given = in.lines() == null ? List.of() : in.lines();
        if (given.size() > MAX_LINES) throw ApiException.badRequest(I18n.t("That is too many different products for one order"));
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (PublicLine l : given) {
            if (l.productId() == null || l.quantity() == null) continue;
            int q = Math.max(1, Math.min(MAX_QUANTITY, l.quantity().setScale(0, RoundingMode.DOWN).intValue()));
            boolean onSale = listings.findByProductId(l.productId()).filter(OnlineListing::isListed).filter(x -> x.getProduct().isActive()).isPresent();
            if (onSale) wanted.merge(l.productId(), q, (a, b) -> Math.min(MAX_QUANTITY, a + b));
        }
        Instant now = Instant.now();
        OnlineCart c = carts.findByEmail(email).orElse(null);
        if (c != null && "STOPPED".equals(c.getStatus())) return;
        if (wanted.isEmpty()) {
            // The customer emptied the cart: whatever was waiting to be reminded about is gone.
            if (c != null && ("ACTIVE".equals(c.getStatus()) || "REMINDED".equals(c.getStatus()))) {
                c.setStatus("EXPIRED");
                carts.save(c);
                cartLines.deleteForCart(c.getId());
            }
            return;
        }
        boolean changed = true;
        if (c == null) {
            c = new OnlineCart();
            c.setEmail(email);
            c.setToken(newToken());
        } else {
            Map<Long, Integer> before = new LinkedHashMap<>();
            for (OnlineCartLine l : cartLines.findByCartIdOrderByIdAsc(c.getId())) before.put(l.getProductId(), l.getQuantity());
            changed = !before.equals(wanted);
            if (("DONE".equals(c.getStatus()) || "EXPIRED".equals(c.getStatus()) || "RECOVERED".equals(c.getStatus()))) {
                Instant last = c.getSecondReminderAt() != null ? c.getSecondReminderAt() : c.getFirstReminderAt();
                if (last == null || last.isBefore(now.minus(AGAIN_AFTER))) {
                    c.setStatus("ACTIVE");
                    c.setReminderCount(0);
                    c.setFirstReminderAt(null);
                    c.setSecondReminderAt(null);
                    c.setRecoveredAt(null);
                    c.setAttempts(0);
                    c.setNextTryAt(null);
                    c.setLastError(null);
                    changed = true;
                }
            }
        }
        c.setLanguage("fr".equalsIgnoreCase(in.language()) ? "fr" : "en");
        if (changed) c.setSavedAt(now);
        c = carts.save(c);
        if (changed) {
            cartLines.deleteForCart(c.getId());
            for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
                OnlineCartLine l = new OnlineCartLine();
                l.setCartId(c.getId());
                l.setProductId(e.getKey());
                l.setQuantity(e.getValue());
                cartLines.save(l);
            }
        }
    }

    /** The cart behind the link of a reminder, as it is on the shop today. */
    @Transactional
    public RestoredCart restore(String token) {
        OnlineStoreSettings s = storeService.settings();
        if (!s.isEnabled()) throw ApiException.notFound(I18n.t("This shop is not available."));
        OnlineCart c = find(token);
        Built b = build(c, s);
        return new RestoredCart(b.lines(), b.gone(), c.getEmail());
    }

    /** The link "stop these reminders". The address is remembered as stopped and the cart is forgotten. */
    @Transactional
    public void stop(String token) {
        OnlineCart c = find(token);
        c.setStatus("STOPPED");
        carts.save(c);
        cartLines.deleteForCart(c.getId());
    }

    private OnlineCart find(String token) {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty() || t.length() > 48) throw ApiException.notFound(I18n.t("This link is not valid, or it was already used."));
        return carts.findByToken(t).orElseThrow(() -> ApiException.notFound(I18n.t("This link is not valid, or it was already used.")));
    }

    // ------------------------------------------------------------------ the job

    /** One pass: sends the reminders that are due (at most one batch) and forgets old carts. Returns how many e-mails went out. */
    public int runPass(Instant now) {
        OnlineStoreSettings s = storeService.settings();
        if (!s.isEnabled()) return 0;
        tidy(now);
        if (!s.isCartRemindersEnabled() || !mailService.isConfigured()) return 0;
        List<OnlineCart> due = carts.due(now.minus(FIRST_AFTER), now.minus(SECOND_AFTER), now, PageRequest.of(0, batchSize));
        int sent = 0;
        for (OnlineCart c : due) {
            Long id = c.getId();
            Prepared p;
            try {
                p = tx.execute(st -> prepare(id, now));
            } catch (RuntimeException e) {
                log.warn("A cart reminder could not be prepared: {}", e.getMessage());
                continue;
            }
            if (p == null) continue;
            String error = null;
            try {
                mailService.send(p.email(), p.subject(), p.html(), List.of());
            } catch (RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("A cart reminder could not be sent: {}", error);
            }
            final String err = error;
            tx.executeWithoutResult(st -> finish(id, err, now));
            if (error == null) sent++;
        }
        return sent;
    }

    /** Checks one due cart and claims it (so a crash or a second server never sends it twice at once); returns the e-mail, or null when nothing is to be sent. */
    private Prepared prepare(Long id, Instant now) {
        OnlineCart c = carts.findById(id).orElse(null);
        if (c == null) return null;
        boolean second = c.getReminderCount() == 1;
        if (!second && !"ACTIVE".equals(c.getStatus()) || second && !"REMINDED".equals(c.getStatus())) return null;
        Duration age = Duration.between(c.getSavedAt(), now);
        if (age.compareTo(second ? SECOND_TOO_LATE : FIRST_TOO_LATE) > 0) {
            c.setStatus("EXPIRED");
            carts.save(c);
            return null;
        }
        if (orders.existsByCustomerEmailIgnoreCaseAndCreatedAtAfter(c.getEmail(), c.getSavedAt())) {
            c.setStatus("RECOVERED");
            c.setRecoveredAt(now);
            carts.save(c);
            return null;
        }
        OnlineStoreSettings s = storeService.settings();
        Built b = build(c, s);
        if (b.lines().isEmpty()) {
            c.setStatus("EXPIRED");
            c.setLastError(null);
            carts.save(c);
            return null;
        }
        c.setNextTryAt(now.plus(RETRY_AFTER));
        carts.save(c);
        String shop = shopName(s);
        return compose(c, second, b, shop, storeService.currencyCode());
    }

    private void finish(Long id, String error, Instant now) {
        OnlineCart c = carts.findById(id).orElse(null);
        if (c == null) return;
        if (error == null) {
            c.setReminderCount(c.getReminderCount() + 1);
            if (c.getReminderCount() == 1) c.setFirstReminderAt(now);
            else c.setSecondReminderAt(now);
            c.setStatus(c.getReminderCount() >= 2 ? "DONE" : "REMINDED");
            c.setAttempts(0);
            c.setNextTryAt(null);
            c.setLastError(null);
        } else {
            c.setAttempts(c.getAttempts() + 1);
            c.setLastError(error.length() > 200 ? error.substring(0, 200) : error);
            if (c.getAttempts() >= 3) c.setStatus("EXPIRED");
        }
        carts.save(c);
    }

    /** Carts untouched for two months are deleted (a STOPPED one stays: it is the memory that the address wants no reminders). */
    private void tidy(Instant now) {
        try {
            List<Long> old = tx.execute(st -> carts.idsNotTouchedSince(now.minus(KEEP_FOR)));
            if (old != null && !old.isEmpty()) tx.executeWithoutResult(st -> carts.deleteByIds(old));
        } catch (RuntimeException e) {
            log.warn("Old carts could not be deleted: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ building the e-mail

    private record Built(List<ReorderLine> lines, List<String> gone) {
    }

    private record Prepared(String email, String subject, String html) {
    }

    /** The cart as the shop has it today: current prices; what is sold out, unlisted or gone is set apart. */
    private Built build(OnlineCart c, OnlineStoreSettings s) {
        List<ReorderLine> lines = new ArrayList<>();
        List<String> gone = new ArrayList<>();
        for (OnlineCartLine l : cartLines.findByCartIdOrderByIdAsc(c.getId())) {
            OnlineListing listing = listings.findByProductId(l.getProductId()).orElse(null);
            if (listing == null) continue;
            boolean onSale = listing.isListed() && listing.getProduct().isActive();
            PublicProduct p = storeService.publicProduct(listing, s);
            if (!onSale || "SOLD_OUT".equals(p.stock())) {
                gone.add(p.name());
                continue;
            }
            lines.add(new ReorderLine(p, BigDecimal.valueOf(l.getQuantity())));
        }
        return new Built(lines, gone);
    }

    private Prepared compose(OnlineCart c, boolean second, Built b, String shop, String currency) {
        String lang = c.getLanguage();
        String subject = second ? I18n.tIn(lang, "Your cart at {0} is still waiting for you", shop) : I18n.tIn(lang, "You left something in your cart at {0}", shop);
        String intro = second ? I18n.tIn(lang, "A last reminder: your cart is still waiting. Items can sell out, so do not wait too long.")
                : I18n.tIn(lang, "You started an order but did not finish it. Your cart is saved, and these items are still on sale:");
        StringBuilder sb = new StringBuilder();
        sb.append("<p>").append(escape(intro)).append("</p><ul>");
        BigDecimal total = BigDecimal.ZERO;
        for (ReorderLine l : b.lines()) {
            BigDecimal line = l.product().price().multiply(l.quantity());
            total = total.add(line);
            sb.append("<li>").append(escape(l.product().name())).append(" &times; ").append(l.quantity().toBigInteger()).append(" &mdash; ")
                    .append(escape(money(line) + " " + currency)).append("</li>");
        }
        sb.append("</ul><p><strong>").append(escape(I18n.tIn(lang, "Total: {0}", money(total) + " " + currency))).append("</strong></p>");
        if (!b.gone().isEmpty()) {
            sb.append("<p>").append(escape(I18n.tIn(lang, "No longer available: {0}", String.join(", ", b.gone())))).append("</p>");
        }
        String link = frontendUrl + "/shop/cart/" + c.getToken();
        sb.append("<p><a href=\"").append(escape(link)).append("\" style=\"display:inline-block;padding:10px 18px;background:#1b6ef3;color:#ffffff;text-decoration:none;border-radius:6px;\">")
                .append(escape(I18n.tIn(lang, "Go back to my cart"))).append("</a></p>");
        sb.append("<p style=\"color:#666;font-size:12px;\">").append(escape(shop)).append("<br>")
                .append(escape(I18n.tIn(lang, "You get this message because you typed your e-mail address when you started an order. To stop these reminders, open this link:")))
                .append(" <a href=\"").append(escape(frontendUrl + "/shop/cart/stop/" + c.getToken())).append("\">")
                .append(escape(I18n.tIn(lang, "Stop these reminders"))).append("</a></p>");
        return new Prepared(c.getEmail(), subject, sb.toString());
    }

    private String shopName(OnlineStoreSettings s) {
        String name = s.getShopName() != null && !s.getShopName().isBlank() ? s.getShopName() : storeSettingsService.getSettingsEntity().getBusinessName();
        return name == null ? "" : name;
    }

    static String money(BigDecimal v) {
        BigDecimal x = v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (x.scale() < 0) x = x.setScale(0);
        return x.toPlainString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String newToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private void throttle(String ip) {
        long now = System.currentTimeMillis();
        synchronized (byIp) {
            if (byIp.size() > 5000) byIp.values().removeIf(d -> d.isEmpty() || now - d.peekLast() > HOUR);
            Deque<Long> d = byIp.computeIfAbsent("c:" + (ip == null ? "?" : ip), k -> new ArrayDeque<>());
            while (!d.isEmpty() && now - d.peekFirst() > HOUR) d.pollFirst();
            if (d.size() >= 120) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, I18n.t("Too many requests from your connection. Please try again later."));
            d.addLast(now);
        }
    }

    // ------------------------------------------------------------------ the staff

    /** The carts of the last weeks and how many are in each state. status empty = all. */
    @Transactional(readOnly = true)
    public CartList list(String status) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] r : carts.counts()) counts.put((String) r[0], ((Number) r[1]).longValue());
        List<OnlineCart> rows = status == null || status.isBlank() ? carts.findAllByOrderBySavedAtDesc(PageRequest.of(0, 200))
                : carts.findByStatusOrderBySavedAtDesc(status.trim().toUpperCase(Locale.ROOT), PageRequest.of(0, 200));
        Map<Long, long[]> totals = new HashMap<>();
        if (!rows.isEmpty()) {
            for (Object[] r : cartLines.totals(rows.stream().map(OnlineCart::getId).toList())) {
                totals.put(((Number) r[0]).longValue(), new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
            }
        }
        List<CartDto> items = rows.stream().map(c -> {
            long[] t = totals.getOrDefault(c.getId(), new long[]{0, 0});
            return new CartDto(c.getId(), c.getEmail(), c.getStatus(), (int) t[0], (int) t[1], c.getSavedAt(), c.getReminderCount(), c.getFirstReminderAt(),
                    c.getSecondReminderAt(), c.getRecoveredAt(), c.getLastError());
        }).toList();
        return new CartList(counts.getOrDefault("ACTIVE", 0L), counts.getOrDefault("REMINDED", 0L), counts.getOrDefault("DONE", 0L),
                counts.getOrDefault("RECOVERED", 0L), counts.getOrDefault("STOPPED", 0L), counts.getOrDefault("EXPIRED", 0L), items);
    }

    /** Removes a cart (and with it the address). A person who asks to be forgotten is removed this way; the reminders could start again if they type the address again. */
    @Transactional
    public void delete(Long id) {
        OnlineCart c = carts.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("That cart was not found.")));
        cartLines.deleteForCart(c.getId());
        carts.delete(c);
        auditLogService.record("ONLINE_CART_DELETED", "An abandoned cart was removed", "OnlineCart", id);
    }
}
