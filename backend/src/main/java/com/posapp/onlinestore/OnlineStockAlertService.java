package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.StockAlertDto;
import com.posapp.onlinestore.OnlineStoreDtos.StockAlertRequest;
import com.posapp.onlinestore.OnlineStoreDtos.StockAlertResult;
import com.posapp.onlinestore.OnlineStoreDtos.StockDemandDto;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * "Tell me when it is back in stock" (v1.54.0). A visitor leaves an e-mail address and/or a phone number on a sold-out product. Every few minutes
 * {@link OnlineStockAlertJob} looks at the waiting requests: once the shop can sell the product again, each customer gets ONE e-mail (and one text message
 * when the shop sends texts) with a link to the product, and the request is closed. Staff see who waits for what (Online Store > Waiting customers), can
 * write to them through WhatsApp and mark them as told.
 */
@Service
public class OnlineStockAlertService {

    private static final Logger log = LoggerFactory.getLogger(OnlineStockAlertService.class);
    private static final long HOUR = 3_600_000L;
    static final int MAX_ATTEMPTS = 3;

    private final OnlineStockAlertRepository alerts;
    private final OnlineListingRepository listings;
    private final OnlineStoreService store;
    private final MailService mailService;
    private final OnlineTextService texts;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService audit;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public OnlineStockAlertService(OnlineStockAlertRepository alerts, OnlineListingRepository listings, OnlineStoreService store, MailService mailService,
                                   OnlineTextService texts, StoreSettingsService storeSettingsService, AuditLogService audit) {
        this.alerts = alerts;
        this.listings = listings;
        this.store = store;
        this.mailService = mailService;
        this.texts = texts;
        this.storeSettingsService = storeSettingsService;
        this.audit = audit;
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

    /** The product is shown on the shop and the shop can sell it. */
    private Optional<Product> onShop(Long productId) {
        return listings.findByProductId(productId).filter(OnlineListing::isListed).map(OnlineListing::getProduct).filter(Product::isActive);
    }

    /** True when the shop can sell at least one unit now (stock not tracked counts as available). */
    private boolean available(Product p, int buffer) {
        BigDecimal sellable = store.sellable(p, buffer);
        return sellable == null || sellable.signum() > 0;
    }

    // ------------------------------------------------------------------ the visitor

    @Transactional
    public StockAlertResult request(StockAlertRequest in, String ip) {
        OnlineStoreSettings s = store.openSettings();
        throttle("a:" + ip, 10);
        if (in.website() != null && !in.website().isBlank()) return new StockAlertResult("SAVED");
        String email = clean(in.email(), 160).toLowerCase(Locale.ROOT);
        String phone = clean(in.phone(), 40);
        if (email.isEmpty() && phone.replaceAll("[^0-9]", "").length() < 6) {
            throw ApiException.badRequest(I18n.t("Give an e-mail address or a phone number so we can tell you."));
        }
        if (!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest(I18n.t("Enter a valid e-mail address."));
        Product p = onShop(in.productId()).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        if (available(p, s.getStockBuffer())) return new StockAlertResult("IN_STOCK");
        if (!email.isEmpty() && alerts.findFirstByProductIdAndEmailAndStatus(p.getId(), email, "WAITING").isPresent()) return new StockAlertResult("SAVED");
        if (email.isEmpty() && alerts.findFirstByProductIdAndPhoneAndStatus(p.getId(), phone, "WAITING").isPresent()) return new StockAlertResult("SAVED");
        OnlineStockAlert a = new OnlineStockAlert();
        a.setProductId(p.getId());
        a.setProductName(clean(p.getName(), 200));
        a.setEmail(email.isEmpty() ? null : email);
        a.setPhone(phone.isEmpty() ? null : phone);
        a.setLanguage("fr".equalsIgnoreCase(in.language()) ? "fr" : "en");
        a.setSourceIp(ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip);
        alerts.save(a);
        return new StockAlertResult("SAVED");
    }

    // ------------------------------------------------------------------ the automatic notice

    /**
     * One pass over the waiting requests: tells every customer whose product can be sold again. Returns how many customers were told. Does nothing while
     * the shop is off. A customer is never told twice; a request whose messages cannot be sent is tried {@value #MAX_ATTEMPTS} times, then marked FAILED.
     */
    @Transactional
    public int notifyAvailable() {
        OnlineStoreSettings s;
        try {
            s = store.openSettings();
        } catch (RuntimeException e) {
            return 0; // the shop is off
        }
        boolean mail = mailService.isConfigured();
        boolean text = texts.enabled();
        if (!mail && !text) return 0;
        Map<Long, Boolean> back = new HashMap<>();
        int told = 0;
        for (OnlineStockAlert a : alerts.findByStatusOrderByIdAsc("WAITING")) {
            Product p = onShop(a.getProductId()).orElse(null);
            if (p == null) continue;
            boolean now = back.computeIfAbsent(a.getProductId(), id -> available(p, s.getStockBuffer()));
            if (!now) continue;
            boolean emailOk = a.getEmail() != null && mail;
            boolean textOk = a.getPhone() != null && text;
            if (!emailOk && !textOk) continue; // nothing this shop can send to this customer: staff can use WhatsApp
            boolean sent = false;
            if (emailOk) sent |= sendMail(s, a);
            if (textOk) sent |= sendText(a);
            if (sent) {
                a.setStatus("NOTIFIED");
                a.setNotifiedAt(Instant.now());
                a.setNotifiedBy("automatic");
                told++;
            } else {
                a.setAttempts(a.getAttempts() + 1);
                if (a.getAttempts() >= MAX_ATTEMPTS) a.setStatus("FAILED");
            }
            alerts.save(a);
        }
        if (told > 0) log.info("{} customer(s) were told that a product is back in stock", told);
        return told;
    }

    private String shop(OnlineStoreSettings s) {
        String name = s.getShopName() != null && !s.getShopName().isBlank() ? s.getShopName() : storeSettingsService.getSettingsEntity().getBusinessName();
        return name == null ? "" : name.trim();
    }

    private static String link(Long productId) {
        String base = OnlineMessages.baseUrl();
        return base.isEmpty() ? "" : base + "/shop?product=" + productId;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private boolean sendMail(OnlineStoreSettings s, OnlineStockAlert a) {
        try {
            String shop = shop(s);
            String lang = a.getLanguage();
            String url = link(a.getProductId());
            String body = "<p>" + escape(I18n.tIn(lang, "Good news: {0} is back in stock.", a.getProductName())) + "</p>"
                    + (url.isEmpty() ? "" : "<p><a href=\"" + escape(url) + "\">" + escape(I18n.tIn(lang, "See it in the shop")) + "</a></p>")
                    + "<p style=\"color:#666; font-size:0.9em;\">" + escape(I18n.tIn(lang, "Stock can run out again: order soon. You asked to be told once, so you will not get more e-mails about this product.")) + "</p>"
                    + (shop.isEmpty() ? "" : "<p>" + escape(shop) + "</p>");
            mailService.send(a.getEmail(), I18n.tIn(lang, "{0} is back in stock", a.getProductName()) + (shop.isEmpty() ? "" : " - " + shop), body, List.of());
            return true;
        } catch (RuntimeException e) {
            log.warn("The back-in-stock e-mail could not be sent: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendText(OnlineStockAlert a) {
        String url = link(a.getProductId());
        String text = I18n.tIn(a.getLanguage(), "{0} is back in stock.", a.getProductName()) + (url.isEmpty() ? "" : " " + url);
        return texts.send("STOCK", a.getPhone(), texts.withShop(text), null, null).sent();
    }

    // ------------------------------------------------------------------ staff

    /** The products customers are waiting for, most wanted first, with the stock word each has now. */
    @Transactional(readOnly = true)
    public List<StockDemandDto> demand() {
        OnlineStoreSettings s = store.settings();
        List<StockDemandDto> out = new ArrayList<>();
        for (Object[] r : alerts.demand()) {
            Long id = ((Number) r[0]).longValue();
            String stock = onShop(id).map(p -> OnlineStoreService.stockLabel(store.sellable(p, s.getStockBuffer()))).orElse("OFF");
            out.add(new StockDemandDto(id, (String) r[1], ((Number) r[2]).longValue(), stock));
        }
        return out;
    }

    /** status: WAITING, NOTIFIED, FAILED or empty for all; productId narrows to one product; q searches name, e-mail and phone. */
    @Transactional(readOnly = true)
    public List<StockAlertDto> list(String status, Long productId, String q) {
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return alerts.findAllByOrderByIdDesc().stream()
                .filter(a -> st.isEmpty() || a.getStatus().equals(st))
                .filter(a -> productId == null || productId.equals(a.getProductId()))
                .filter(a -> needle.isEmpty() || (a.getProductName() + " " + nz(a.getEmail()) + " " + nz(a.getPhone())).toLowerCase(Locale.ROOT).contains(needle))
                .map(OnlineStockAlertService::dto).toList();
    }

    /** Staff told the customer themselves (a call, a WhatsApp message): the request is closed. */
    @Transactional
    public StockAlertDto told(Long id) {
        OnlineStockAlert a = find(id);
        a.setStatus("NOTIFIED");
        a.setNotifiedAt(Instant.now());
        a.setNotifiedBy(who());
        return dto(alerts.save(a));
    }

    @Transactional
    public void delete(Long id) {
        OnlineStockAlert a = find(id);
        alerts.delete(a);
        audit.record("ONLINE_STOCK_ALERT_DELETE", "Back-in-stock request for " + a.getProductName() + " removed", "OnlineStockAlert", id);
    }

    private OnlineStockAlert find(Long id) {
        return alerts.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static String who() {
        org.springframework.security.core.Authentication a = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
    }

    private static StockAlertDto dto(OnlineStockAlert a) {
        return new StockAlertDto(a.getId(), a.getProductId(), a.getProductName(), a.getEmail(), a.getPhone(), a.getLanguage(), a.getStatus(), a.getCreatedAt(),
                a.getNotifiedAt(), a.getNotifiedBy());
    }
}
