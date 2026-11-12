package com.posapp.onlinestore;

import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coupons on the public shop. One place decides what a code is worth, for the preview at checkout and for the order itself, so the two can never disagree:
 * the preview only asks, the order asks again under a lock on the coupon (so the last use cannot be taken twice) and then keeps the answer.
 * The customer's browser never says how much the discount is.
 */
@Service
public class OnlineCouponService {

    /** Orders in these states no longer hold a use of their coupon. */
    static final List<String> RELEASED = List.of("REJECTED", "CANCELLED");

    /** discount comes off the items; feeWaived off the delivery fee. */
    public record Applied(OnlineCoupon coupon, BigDecimal discount, BigDecimal feeWaived) {
        public BigDecimal savings() {
            return discount.add(feeWaived);
        }
    }

    private final OnlineCouponRepository coupons;
    private final OnlineOrderRepository orders;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public OnlineCouponService(OnlineCouponRepository coupons, OnlineOrderRepository orders) {
        this.coupons = coupons;
        this.orders = orders;
    }

    public boolean anyActive() {
        return coupons.existsByActiveTrue();
    }

    /** Slows down someone trying codes one after another. */
    void throttle(String ip, int perHour) {
        long now = System.currentTimeMillis();
        synchronized (hits) {
            if (hits.size() > 20_000) hits.clear();
            Deque<Long> d = hits.computeIfAbsent("k:" + ip, k -> new ArrayDeque<>());
            while (!d.isEmpty() && now - d.peekFirst() > 3_600_000L) d.pollFirst();
            if (d.size() >= perHour) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, I18n.t("Too many requests from your connection. Please try again later."));
            d.addLast(now);
        }
    }

    static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** The last 9 digits of a phone number: 0788 123 456 and +250 788 123 456 are the same person. */
    static String phoneKey(String phone) {
        String d = phone == null ? "" : phone.replaceAll("\\D", "");
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    /**
     * What the code is worth on this order, or an ApiException saying why not.
     *
     * @param subtotal the items before any discount
     * @param fee      the delivery fee the customer would pay without the coupon
     * @param phone    may be empty while the customer is still typing: then "once per customer" is checked only when the order is placed
     * @param lock     true when placing the order: the coupon row is locked so the count of uses cannot change under us
     */
    @Transactional
    public Applied evaluate(String rawCode, BigDecimal subtotal, BigDecimal fee, String method, String phone, Long shopCustomerId, boolean lock) {
        String code = normalize(rawCode);
        if (code.isEmpty()) throw ApiException.badRequest(I18n.t("Enter a coupon code"));
        OnlineCoupon c = (lock ? coupons.lockByCode(code) : coupons.findByCode(code)).filter(OnlineCoupon::isActive)
                .orElseThrow(() -> ApiException.badRequest(I18n.t("This coupon code is not valid.")));
        LocalDate today = LocalDate.now();
        if (c.getValidFrom() != null && today.isBefore(c.getValidFrom())) throw ApiException.badRequest(I18n.t("This coupon is not valid yet. It starts on {0}.", c.getValidFrom()));
        if (c.getValidUntil() != null && today.isAfter(c.getValidUntil())) throw ApiException.badRequest(I18n.t("This coupon has expired."));
        if (c.getMinOrder() != null && c.getMinOrder().signum() > 0 && subtotal.compareTo(c.getMinOrder()) < 0) {
            throw ApiException.badRequest(I18n.t("This coupon needs an order of at least {0}.", OnlineStoreService.money(c.getMinOrder())));
        }
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal waived = BigDecimal.ZERO;
        switch (c.getType()) {
            case "PERCENT" -> {
                discount = subtotal.multiply(c.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (c.getMaxDiscount() != null && c.getMaxDiscount().signum() > 0) discount = discount.min(c.getMaxDiscount());
            }
            case "FIXED" -> discount = c.getValue();
            case "FREE_DELIVERY" -> {
                if (!"DELIVERY".equals(method)) throw ApiException.badRequest(I18n.t("This coupon is for delivery orders."));
                if (fee.signum() <= 0) throw ApiException.badRequest(I18n.t("Delivery is already free for this order."));
                waived = fee;
            }
            default -> throw ApiException.badRequest(I18n.t("This coupon code is not valid."));
        }
        discount = discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
        if (c.getMaxUses() != null && orders.countByCouponIdAndStatusNotIn(c.getId(), RELEASED) >= c.getMaxUses()) {
            throw ApiException.badRequest(I18n.t("This coupon has been used up."));
        }
        if (c.isOncePerCustomer()) {
            String key = phoneKey(phone);
            boolean used = !key.isEmpty() && orders.countByCouponIdAndCouponKeyAndStatusNotIn(c.getId(), key, RELEASED) > 0;
            if (!used && shopCustomerId != null) used = orders.countByCouponIdAndShopCustomerIdAndStatusNotIn(c.getId(), shopCustomerId, RELEASED) > 0;
            if (used) throw ApiException.badRequest(I18n.t("You have already used this coupon."));
        }
        return new Applied(c, discount, waived);
    }

    /**
     * Shares a discount over the lines in proportion to what each costs, to the cent, so the parts always add up to the whole.
     * The last line takes what is left; if rounding would put more on it than it costs, the rest goes line by line.
     */
    static List<BigDecimal> allocate(List<BigDecimal> lineTotals, BigDecimal discount) {
        List<BigDecimal> out = new ArrayList<>();
        BigDecimal sum = lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (discount.signum() <= 0 || sum.signum() <= 0) {
            lineTotals.forEach(t -> out.add(BigDecimal.ZERO.setScale(2)));
            return out;
        }
        BigDecimal given = BigDecimal.ZERO;
        boolean ok = true;
        for (int i = 0; i < lineTotals.size(); i++) {
            BigDecimal part = i == lineTotals.size() - 1 ? discount.subtract(given)
                    : discount.multiply(lineTotals.get(i)).divide(sum, 2, RoundingMode.HALF_UP);
            if (part.signum() < 0 || part.compareTo(lineTotals.get(i)) > 0) ok = false;
            out.add(part);
            given = given.add(part);
        }
        if (ok) return out;
        out.clear();
        BigDecimal left = discount;
        for (BigDecimal t : lineTotals) {
            BigDecimal part = left.min(t);
            out.add(part);
            left = left.subtract(part);
        }
        return out;
    }
}
