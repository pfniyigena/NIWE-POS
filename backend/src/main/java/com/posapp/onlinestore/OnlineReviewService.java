package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.settings.StoreSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Customer reviews (v1.35.0). Only someone who bought the product on a completed order can review it (the order code plus the order's phone number, or the
 * signed-in account that holds the order), once per product per order. A review waits for staff: only approved ones are shown on the shop.
 */
@Service
public class OnlineReviewService {

    private static final long HOUR = 3_600_000L;

    private final OnlineReviewRepository reviews;
    private final OnlineOrderRepository orders;
    private final OnlineStoreService storeService;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public OnlineReviewService(OnlineReviewRepository reviews, OnlineOrderRepository orders, OnlineStoreService storeService, MailService mailService,
                               StoreSettingsService storeSettingsService, AuditLogService auditLogService) {
        this.reviews = reviews;
        this.orders = orders;
        this.storeService = storeService;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
    }

    private static String user() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
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

    private OnlineStoreSettings openSettings() {
        OnlineStoreSettings s = storeService.settings();
        if (!s.isEnabled()) throw ApiException.notFound(I18n.t("This shop is not available."));
        return s;
    }

    private static String key(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        return digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
    }

    private OnlineOrder order(String code) {
        return orders.findByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound(I18n.t("We could not find that order. Check the code.")));
    }

    // ------------------------------------------------------------------ the customer

    /** What can be reviewed on an order, and what was already said. The code is the key, as for following an order. */
    @Transactional(readOnly = true)
    public ReviewOptions options(String code, String ip) {
        OnlineStoreSettings s = openSettings();
        throttle("t:" + ip, Math.max(60, s.getMaxPerIpPerHour() * 5));
        OnlineOrder o = order(code);
        if (!"COMPLETED".equals(o.getStatus())) return new ReviewOptions(false, I18n.t("You can review the products once your order is completed."), List.of());
        Map<Long, OnlineReview> mine = new HashMap<>();
        for (OnlineReview r : reviews.findByOnlineOrderId(o.getId())) mine.put(r.getProductId(), r);
        Map<Long, ReviewItem> items = new LinkedHashMap<>();
        for (OnlineOrderLine l : o.getLines()) {
            if (l.getProductId() == null || items.containsKey(l.getProductId())) continue;
            OnlineReview r = mine.get(l.getProductId());
            items.put(l.getProductId(), new ReviewItem(l.getProductId(), l.getName(), r == null ? null : r.getRating(), r == null ? null : r.getReviewText(), r == null ? null : r.getStatus()));
        }
        return new ReviewOptions(true, null, new ArrayList<>(items.values()));
    }

    /** A review of one product of a completed order. It waits for staff. */
    @Transactional
    public ReviewItem submit(String code, ReviewRequest in, String ip, Long shopCustomerId) {
        OnlineStoreSettings s = openSettings();
        throttle("v:" + ip, 20);
        OnlineOrder o = order(code);
        boolean owner = shopCustomerId != null && shopCustomerId.equals(o.getShopCustomerId());
        if (!owner && (key(in.phone()).length() < 6 || !key(in.phone()).equals(o.getPhoneKey()))) {
            throw ApiException.badRequest(I18n.t("The phone number does not match this order."));
        }
        if (!"COMPLETED".equals(o.getStatus())) throw ApiException.badRequest(I18n.t("You can review the products once your order is completed."));
        OnlineOrderLine line = o.getLines().stream().filter(l -> l.getProductId() != null && l.getProductId().equals(in.productId())).findFirst()
                .orElseThrow(() -> ApiException.badRequest(I18n.t("That item is not on this order.")));
        if (in.rating() < 1 || in.rating() > 5) throw ApiException.badRequest(I18n.t("Choose a rating from 1 to 5 stars."));
        if (reviews.findByOnlineOrderIdAndProductId(o.getId(), line.getProductId()).isPresent()) {
            throw ApiException.badRequest(I18n.t("You already reviewed this product."));
        }
        String text = in.text() == null ? "" : in.text().trim();
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty()) {
            String full = o.getCustomerName() == null ? "" : o.getCustomerName().trim();
            name = full.isEmpty() ? I18n.t("Customer") : full.split("\\s+")[0];
        }
        OnlineReview r = new OnlineReview();
        r.setOnlineOrderId(o.getId());
        r.setProductId(line.getProductId());
        r.setProductName(line.getName() == null ? "" : line.getName().length() > 160 ? line.getName().substring(0, 160) : line.getName());
        r.setShopCustomerId(owner ? shopCustomerId : null);
        r.setReviewerName(name.length() > 60 ? name.substring(0, 60) : name);
        r.setRating(in.rating());
        r.setReviewText(text.isEmpty() ? null : text.length() > 1000 ? text.substring(0, 1000) : text);
        reviews.save(r);
        auditLogService.record("ONLINE_REVIEW_SUBMITTED", "Review of " + r.getProductName() + " (" + r.getRating() + "/5) on online order " + o.getCode(), "OnlineReview", r.getId());
        OnlineMessages.notifyShopNote(mailService, storeSettingsService, s, I18n.t("New review waiting"),
                I18n.t("{0} reviewed {1} ({2} of 5 stars). Open Online Store > Reviews to approve it or hide it.", r.getReviewerName(), r.getProductName(), r.getRating()));
        return new ReviewItem(r.getProductId(), r.getProductName(), r.getRating(), r.getReviewText(), r.getStatus());
    }

    // ------------------------------------------------------------------ staff

    @Transactional(readOnly = true)
    public List<ReviewDto> list(String q, String status) {
        List<OnlineReview> all = status == null || status.isBlank() || status.equals("ALL") ? reviews.findAllByOrderByIdDesc()
                : reviews.findByStatusOrderByIdDesc(status.trim().toUpperCase(Locale.ROOT));
        Map<Long, String> codes = new HashMap<>();
        orders.findAllById(all.stream().map(OnlineReview::getOnlineOrderId).distinct().toList()).forEach(o -> codes.put(o.getId(), o.getCode()));
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return all.stream().map(r -> toDto(r, codes.get(r.getOnlineOrderId())))
                .filter(d -> needle.isEmpty() || (d.orderCode() + " " + d.productName() + " " + d.reviewerName() + " " + (d.text() == null ? "" : d.text())).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        return Map.of("reviewsPending", reviews.countByStatus("PENDING"));
    }

    @Transactional
    public ReviewDto approve(Long id) {
        return moderate(id, "APPROVED");
    }

    @Transactional
    public ReviewDto hide(Long id) {
        return moderate(id, "HIDDEN");
    }

    private ReviewDto moderate(Long id, String status) {
        OnlineReview r = reviews.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Review not found")));
        r.setStatus(status);
        r.setModeratedBy(user());
        r.setModeratedAt(Instant.now());
        reviews.save(r);
        auditLogService.record("ONLINE_REVIEW_" + status, "Review of " + r.getProductName() + " by " + r.getReviewerName() + " set to " + status, "OnlineReview", id);
        OnlineOrder o = orders.findById(r.getOnlineOrderId()).orElse(null);
        return toDto(r, o == null ? null : o.getCode());
    }

    @Transactional
    public void delete(Long id) {
        OnlineReview r = reviews.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Review not found")));
        reviews.delete(r);
        auditLogService.record("ONLINE_REVIEW_DELETED", "Deleted the review of " + r.getProductName() + " by " + r.getReviewerName(), "OnlineReview", id);
    }

    private static ReviewDto toDto(OnlineReview r, String orderCode) {
        return new ReviewDto(r.getId(), orderCode, r.getProductId(), r.getProductName(), r.getReviewerName(), r.getRating(), r.getReviewText(), r.getStatus(),
                r.getCreatedAt(), r.getModeratedBy());
    }
}
