package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Loyalty points for signed-in shop customers (v1.40.0). Earned when an order is completed (points per 100 of what the customer paid for the goods, set in
 * Shop settings, 1 by default), spent at checkout as a discount (1 point = 1 unit of the shop's currency, at most a set share of the goods). Cancelled or
 * rejected orders give the spent points back; returned goods give back a matching share of the spent points and take back a matching share of the earned
 * points. Every change is a row of {@code shop_loyalty_entry}; the account keeps the balance, and spending locks the account, so a balance can never be spent twice.
 * Points do not expire. Guests earn nothing: points belong to an account.
 */
@Service
public class ShopLoyaltyService {

    private final ShopLoyaltyEntryRepository entries;
    private final ShopCustomerRepository customers;
    private final OnlineStoreSettingsRepository settingsRepository;
    private final AuditLogService auditLogService;

    public ShopLoyaltyService(ShopLoyaltyEntryRepository entries, ShopCustomerRepository customers, OnlineStoreSettingsRepository settingsRepository,
                          AuditLogService auditLogService) {
        this.entries = entries;
        this.customers = customers;
        this.settingsRepository = settingsRepository;
        this.auditLogService = auditLogService;
    }

    private OnlineStoreSettings settings() {
        List<OnlineStoreSettings> all = settingsRepository.findAll();
        return all.isEmpty() ? new OnlineStoreSettings() : all.get(0);
    }

    private static String user() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
    }

    private ShopLoyaltyEntry apply(ShopCustomer c, String kind, int points, OnlineOrder o, Long returnId, String note, String by) {
        c.setLoyaltyPoints(c.getLoyaltyPoints() + points);
        customers.save(c);
        ShopLoyaltyEntry e = new ShopLoyaltyEntry();
        e.setShopCustomerId(c.getId());
        e.setKind(kind);
        e.setPoints(points);
        e.setBalanceAfter(c.getLoyaltyPoints());
        if (o != null) {
            e.setOnlineOrderId(o.getId());
            e.setOrderCode(o.getCode());
        }
        e.setOnlineReturnId(returnId);
        e.setNote(note == null ? null : (note.length() > 200 ? note.substring(0, 200) : note));
        e.setCreatedBy(by);
        return entries.save(e);
    }

    // ------------------------------------------------------------------ checkout

    /**
     * How many of the points the customer asked to use may really be used on this order: not more than the balance, and not more than the set share of
     * what is left to pay for the goods. Locks the account until the order is saved, so the same points cannot go into two orders at once.
     */
    @Transactional
    public int usable(Long customerId, int requested, BigDecimal payableGoods, OnlineStoreSettings s) {
        if (customerId == null || requested <= 0 || !s.isLoyaltyEnabled()) return 0;
        ShopCustomer c = customers.lockById(customerId).orElse(null);
        if (c == null) return 0;
        int cap = payableGoods.signum() <= 0 ? 0
                : payableGoods.multiply(BigDecimal.valueOf(Math.max(0, Math.min(100, s.getLoyaltyMaxPercent())))).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).min(BigDecimal.valueOf(Integer.MAX_VALUE)).intValue();
        return Math.max(0, Math.min(requested, Math.min(Math.max(0, c.getLoyaltyPoints()), cap)));
    }

    /** The order was saved with a points discount: take the points off the balance. */
    @Transactional
    public void recordSpend(OnlineOrder o, Long customerId, int points) {
        if (points <= 0) return;
        ShopCustomer c = customers.lockById(customerId).orElseThrow(() -> ApiException.badRequest(I18n.t("That account was not found.")));
        if (c.getLoyaltyPoints() < points) throw ApiException.badRequest(I18n.t("You do not have enough points."));
        apply(c, "SPEND", -points, o, null, null, "customer");
    }

    // ------------------------------------------------------------------ the life of an order

    /** The order was completed: the customer earns points on what they paid for the goods. Returns the points earned (0 when none). Safe to call twice. */
    @Transactional
    public int earn(OnlineOrder o) {
        if (o.getShopCustomerId() == null || o.getId() == null) return 0;
        OnlineStoreSettings s = settings();
        if (!s.isLoyaltyEnabled() || s.getLoyaltyPointsPer100() <= 0) return 0;
        if (entries.existsByOnlineOrderIdAndKindAndOnlineReturnIdIsNull(o.getId(), "EARN")) return 0;
        BigDecimal net = o.getSubtotal().subtract(o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount());
        int points = net.signum() <= 0 ? 0 : net.multiply(BigDecimal.valueOf(s.getLoyaltyPointsPer100())).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(Integer.MAX_VALUE / 2)).intValue();
        if (points <= 0) return 0;
        ShopCustomer c = customers.lockById(o.getShopCustomerId()).orElse(null);
        if (c == null) return 0;
        apply(c, "EARN", points, o, null, null, "system");
        o.setLoyaltyPointsEarned(points);
        return points;
    }

    /** The order was cancelled or rejected: the points spent on it return. Safe to call twice. */
    @Transactional
    public void giveBackSpent(OnlineOrder o) {
        if (o.getLoyaltyPointsUsed() <= 0 || o.getShopCustomerId() == null || o.getId() == null) return;
        if (entries.existsByOnlineOrderIdAndKindAndOnlineReturnIdIsNull(o.getId(), "SPEND_BACK")) return;
        ShopCustomer c = customers.lockById(o.getShopCustomerId()).orElse(null);
        if (c == null) return;
        apply(c, "SPEND_BACK", o.getLoyaltyPointsUsed(), o, null, null, user());
    }

    /**
     * Goods came back (a return was processed): the customer gets back the share of the spent points that belonged to those goods, and the share of the earned
     * points that belonged to them is taken back. Shares follow the money value of the returned goods, never beyond what the order spent or earned.
     */
    @Transactional
    public void afterReturn(OnlineOrder o, OnlineReturn r) {
        if (o.getShopCustomerId() == null || r.getId() == null) return;
        ShopCustomer c = customers.lockById(o.getShopCustomerId()).orElse(null);
        if (c == null) return;
        BigDecimal returnedNet = BigDecimal.ZERO;
        BigDecimal returnedDiscount = BigDecimal.ZERO;
        for (OnlineReturnLine l : r.getLines()) {
            returnedNet = returnedNet.add(l.getAmount());
            OnlineOrderLine ol = o.getLines().stream().filter(x -> x.getId().equals(l.getOnlineOrderLineId())).findFirst().orElse(null);
            if (ol == null || ol.getQuantity().signum() <= 0) continue;
            BigDecimal gross = ol.getLineTotal().multiply(l.getQuantity()).divide(ol.getQuantity(), 2, RoundingMode.HALF_UP);
            returnedDiscount = returnedDiscount.add(gross.subtract(l.getAmount()).max(BigDecimal.ZERO));
        }
        BigDecimal discount = o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount();
        if (o.getLoyaltyPointsUsed() > 0 && discount.signum() > 0 && !entries.existsByOnlineReturnIdAndKind(r.getId(), "RET_BACK")) {
            long back = returnedDiscount.multiply(o.getLoyaltyDiscount()).divide(discount, 0, RoundingMode.DOWN).longValue();
            back = Math.min(back, o.getLoyaltyPointsUsed() - entries.sumByOrderAndKind(o.getId(), "RET_BACK"));
            if (back > 0) apply(c, "RET_BACK", (int) back, o, r.getId(), I18n.t("Return {0}", r.getCode()), user());
        }
        BigDecimal orderNet = o.getSubtotal().subtract(discount);
        if (o.getLoyaltyPointsEarned() > 0 && orderNet.signum() > 0 && !entries.existsByOnlineReturnIdAndKind(r.getId(), "RET_TAKE")) {
            long take = returnedNet.multiply(BigDecimal.valueOf(o.getLoyaltyPointsEarned())).divide(orderNet, 0, RoundingMode.DOWN).longValue();
            take = Math.min(take, o.getLoyaltyPointsEarned() - entries.sumByOrderAndKind(o.getId(), "RET_TAKE"));
            if (take > 0) apply(c, "RET_TAKE", (int) -take, o, r.getId(), I18n.t("Return {0}", r.getCode()), user());
        }
    }

    /** The account is deleted: its history goes with it. */
    @Transactional
    public void deleteFor(Long customerId) {
        entries.deleteForCustomer(customerId);
    }

    // ------------------------------------------------------------------ the customer's view

    private static LoyaltyEntryDto dto(ShopLoyaltyEntry e) {
        return new LoyaltyEntryDto(e.getId(), e.getKind(), e.getPoints(), e.getBalanceAfter(), e.getOrderCode(), e.getNote(), e.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public LoyaltyView view(ShopCustomer c) {
        OnlineStoreSettings s = settings();
        return new LoyaltyView(s.isLoyaltyEnabled(), c.getLoyaltyPoints(), s.getLoyaltyPointsPer100(), s.getLoyaltyMaxPercent(),
                entries.findByShopCustomerIdOrderByIdDesc(c.getId(), PageRequest.of(0, 50)).stream().map(ShopLoyaltyService::dto).toList());
    }

    // ------------------------------------------------------------------ staff

    /** Customers with accounts, those with the most points first. query: part of a name, e-mail or phone number. */
    @Transactional(readOnly = true)
    public LoyaltyList list(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<LoyaltyCustomerDto> items = customers.search(q.isEmpty() ? "" : "%" + q + "%", PageRequest.of(0, 100)).stream()
                .map(c -> new LoyaltyCustomerDto(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getLoyaltyPoints())).toList();
        return new LoyaltyList(customers.countByLoyaltyPointsGreaterThan(0), customers.sumPoints(), items);
    }

    @Transactional(readOnly = true)
    public LoyaltyView ledger(Long customerId) {
        ShopCustomer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound(I18n.t("That account was not found.")));
        return view(c);
    }

    /** Staff add or remove points by hand (a gift, a correction); a reason is needed and the balance cannot go below zero. */
    @Transactional
    public LoyaltyView adjust(Long customerId, LoyaltyAdjust in) {
        int points = in.points() == null ? 0 : in.points();
        String reason = in.reason() == null ? "" : in.reason().trim();
        if (points == 0) throw ApiException.badRequest(I18n.t("Enter a number of points that is not zero."));
        if (Math.abs(points) > 1_000_000) throw ApiException.badRequest(I18n.t("That is too many points at once."));
        if (reason.length() < 3) throw ApiException.badRequest(I18n.t("Tell why the points are changed."));
        ShopCustomer c = customers.lockById(customerId).orElseThrow(() -> ApiException.notFound(I18n.t("That account was not found.")));
        if (c.getLoyaltyPoints() + points < 0) throw ApiException.badRequest(I18n.t("The balance cannot go below zero."));
        apply(c, "ADJUST", points, null, null, reason, user());
        auditLogService.record("LOYALTY_ADJUSTED", "Loyalty points of a shop account changed by " + points + ": " + reason, "ShopCustomer", c.getId());
        return view(c);
    }
}
