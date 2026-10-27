package com.posapp.loyalty;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.loyalty.LoyaltyDtos.*;
import com.posapp.order.OrderCalculator;
import com.posapp.order.OrderRepository;
import com.posapp.order.Payment;
import com.posapp.order.SaleOrder;
import com.posapp.order.Tenders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Loyalty points. A customer earns points on what they spend (every {@code spendAmount} gives {@code pointsEarned}) and can spend points at the till, each worth {@code pointValue}, up
 * to {@code maxRedeemPercent} of one sale. The balance is the sum of the customer's point movements. Spending points is a cost to the shop (it is booked to the rewards expense when
 * the sale is posted), earning costs nothing until the points are spent. Walk-in customers do not earn.
 */
@Service
public class LoyaltyService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_MONEY = new BigDecimal("1000000000");

    private final LoyaltySettingRepository settings;
    private final LoyaltyTxnRepository txns;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final AuditLogService audit;

    public LoyaltyService(LoyaltySettingRepository settings, LoyaltyTxnRepository txns, CustomerRepository customers, OrderRepository orders, AuditLogService audit) {
        this.settings = settings;
        this.txns = txns;
        this.customers = customers;
        this.orders = orders;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ settings

    /** True while the loyalty programme is switched on (Sales > Settings). */
    @Transactional
    public boolean isOn() {
        return setting().isEnabled();
    }

    /** Switched from Sales > Settings; the other loyalty rules are kept as they are. */
    @Transactional
    public void setOn(boolean on) {
        LoyaltySetting s = setting();
        if (s.isEnabled() == on) return;
        s.setEnabled(on);
        settings.save(s);
        audit.record("LOYALTY_SETTINGS_CHANGED", "Loyalty programme switched " + (on ? "on" : "off") + " in Sales settings", "LoyaltySetting", s.getId());
    }

    @Transactional
    public Settings getSettings() {
        return toDto(setting());
    }

    @Transactional
    public Settings updateSettings(Settings r) {
        if (r == null) throw ApiException.badRequest("Nothing to save.");
        if (r.spendAmount() == null || r.spendAmount().signum() <= 0 || r.spendAmount().compareTo(MAX_MONEY) > 0) throw ApiException.badRequest("Enter how much a customer spends to earn points (above zero).");
        if (r.pointsEarned() <= 0 || r.pointsEarned() > 100000) throw ApiException.badRequest("Enter how many points that spending earns (1 or more).");
        if (r.pointValue() == null || r.pointValue().signum() <= 0 || r.pointValue().compareTo(MAX_MONEY) > 0) throw ApiException.badRequest("Enter what one point is worth (above zero).");
        if (r.minRedeemPoints() < 0 || r.minRedeemPoints() > 100000000) throw ApiException.badRequest("The smallest number of points that can be spent cannot be negative.");
        if (r.maxRedeemPercent() == null || r.maxRedeemPercent().signum() <= 0 || r.maxRedeemPercent().compareTo(HUNDRED) > 0) {
            throw ApiException.badRequest("Points can pay between more than 0% and 100% of a sale.");
        }
        LoyaltySetting s = setting();
        s.setEnabled(r.enabled());
        s.setSpendAmount(r.spendAmount().setScale(2, RoundingMode.HALF_UP));
        s.setPointsEarned(r.pointsEarned());
        s.setPointValue(r.pointValue().setScale(4, RoundingMode.HALF_UP));
        s.setMinRedeemPoints(r.minRedeemPoints());
        s.setMaxRedeemPercent(r.maxRedeemPercent().setScale(2, RoundingMode.HALF_UP));
        settings.save(s);
        audit.record("LOYALTY_SETTINGS_CHANGED", "Loyalty programme " + (r.enabled() ? "on" : "off") + ": " + r.pointsEarned() + " point(s) per " + r.spendAmount() + ", a point is worth " + r.pointValue(), "LoyaltySetting", s.getId());
        return toDto(s);
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public Summary summary() {
        long customersWith = 0, points = 0;
        for (Object[] row : txns.customerTotals()) {
            int p = ((Number) row[1]).intValue();
            if (p > 0) {
                customersWith++;
                points += p;
            }
        }
        BigDecimal value = BigDecimal.valueOf(points).multiply(setting().getPointValue()).setScale(2, RoundingMode.HALF_UP);
        return new Summary(customersWith, points, value);
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> customers(String q) {
        LoyaltySetting s = setting();
        Map<Long, Object[]> totals = new HashMap<>();
        for (Object[] row : txns.customerTotals()) totals.put((Long) row[0], row);
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<CustomerRow> out = new ArrayList<>();
        for (Customer c : customers.findAllById(totals.keySet())) {
            Object[] row = totals.get(c.getId());
            int p = ((Number) row[1]).intValue();
            if (!needle.isEmpty() && !((c.getName() == null ? "" : c.getName()) + " " + (c.getPhone() == null ? "" : c.getPhone())).toLowerCase().contains(needle)) continue;
            out.add(new CustomerRow(c.getId(), c.getName(), c.getPhone(), p, worth(p, s), (java.time.Instant) row[2]));
        }
        out.sort(Comparator.comparingInt(CustomerRow::points).reversed().thenComparing(CustomerRow::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return out;
    }

    @Transactional(readOnly = true)
    public CustomerDetail detail(Long customerId) {
        Customer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
        int p = balanceOf(customerId);
        List<TxnRow> history = new ArrayList<>();
        for (LoyaltyTxn t : txns.findByCustomerIdOrderByIdDesc(customerId)) {
            String inv = t.getOrderId() == null ? null : orders.findById(t.getOrderId()).map(SaleOrder::getInvoiceNumber).orElse(null);
            history.add(new TxnRow(t.getId(), t.getType().name(), t.getPoints(), t.getBalanceAfter(), t.getOrderId(), inv, t.getNote(), t.getCreatedAt()));
        }
        return new CustomerDetail(c.getId(), c.getName(), p, worth(p, setting()), history);
    }

    /** What the till shows once a customer is on the cart. */
    @Transactional(readOnly = true)
    public Balance balance(Long customerId) {
        LoyaltySetting s = setting();
        Customer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
        int p = c.isWalkIn() ? 0 : balanceOf(customerId);
        return new Balance(c.getId(), s.isEnabled() && !c.isWalkIn(), p, worth(p, s), s.getPointValue(), s.getMinRedeemPoints(), s.getMaxRedeemPercent());
    }

    @Transactional(readOnly = true)
    public int balanceOf(Long customerId) {
        return txns.balanceOf(customerId).intValue();
    }

    // ------------------------------------------------------------------ by hand

    @Transactional
    public CustomerDetail adjust(Long customerId, AdjustRequest r) {
        if (r == null || r.points() == null || r.points() == 0) throw ApiException.badRequest("Enter the points to add or take away (not zero).");
        if (Math.abs((long) r.points()) > 100000000L) throw ApiException.badRequest("That is too many points.");
        if (r.note() == null || r.note().isBlank()) throw ApiException.badRequest("Write the reason for the change.");
        Customer c = customers.lockById(customerId).orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
        if (c.isWalkIn()) throw ApiException.badRequest("The walk-in customer does not collect points.");
        int current = balanceOf(customerId);
        if (current + r.points() < 0) throw ApiException.badRequest("The customer only has " + current + " point(s); you cannot take away " + (-r.points()) + ".");
        write(customerId, LoyaltyTxnType.ADJUSTED, r.points(), current + r.points(), null, null, trim(r.note()));
        audit.record("LOYALTY_ADJUSTED", (r.points() > 0 ? "Added " : "Took away ") + Math.abs(r.points()) + " point(s) for " + c.getName() + ": " + trim(r.note()), "Customer", customerId);
        return detail(customerId);
    }

    // ------------------------------------------------------------------ at the till

    /**
     * Checks that {@code points} can be spent on a sale of {@code due} by {@code customer} and returns what they are worth. Used while the sale is put together; the points are really
     * taken, under a lock, by {@link #redeemForOrder}.
     */
    @Transactional(readOnly = true)
    public BigDecimal tenderValue(Customer customer, int points, BigDecimal due) {
        LoyaltySetting s = setting();
        if (!s.isEnabled()) throw ApiException.badRequest("The loyalty programme is switched off.");
        if (customer == null || customer.isWalkIn()) throw ApiException.badRequest("Points can only be spent by a named customer. Choose the customer on this cart first.");
        if (points <= 0) throw ApiException.badRequest("Enter the number of points to spend.");
        if (points < s.getMinRedeemPoints()) throw ApiException.badRequest("The smallest number of points that can be spent is " + s.getMinRedeemPoints() + ".");
        int have = balanceOf(customer.getId());
        if (points > have) throw ApiException.badRequest(customer.getName() + " only has " + have + " point(s).");
        BigDecimal value = worth(points, s);
        BigDecimal cap = due.multiply(s.getMaxRedeemPercent()).divide(HUNDRED, 2, RoundingMode.DOWN);
        if (value.compareTo(cap) > 0) {
            throw ApiException.badRequest("Points can pay at most " + s.getMaxRedeemPercent().stripTrailingZeros().toPlainString() + "% of a sale (" + cap.toPlainString() + "). " + points + " points are worth " + value.toPlainString() + ".");
        }
        return value;
    }

    /** Takes the points that paid for a saved sale off the customer. Throws (undoing the sale) if the customer no longer has them. */
    @Transactional
    public void redeemForOrder(SaleOrder saved) {
        int total = 0;
        for (Payment p : saved.getPayments()) {
            if (Tenders.LOYALTY.equals(p.getMethod()) && p.getPoints() != null) total += p.getPoints();
        }
        if (total <= 0) return;
        Customer c = customers.lockById(saved.getCustomer().getId()).orElseThrow(() -> ApiException.badRequest("The customer on this sale no longer exists."));
        int have = balanceOf(c.getId());
        if (total > have) throw ApiException.badRequest(c.getName() + " only has " + have + " point(s) now; the sale needs " + total + ".");
        write(c.getId(), LoyaltyTxnType.REDEEMED, -total, have - total, saved.getId(), null, null);
    }

    /** Gives the customer the points a completed sale earned, and records them on the sale for its receipt. A no-op when the programme is off or the customer is the walk-in. */
    @Transactional
    public void earnForOrder(SaleOrder saved) {
        LoyaltySetting s = setting();
        Customer customer = saved.getCustomer();
        if (!s.isEnabled() || customer == null || customer.isWalkIn()) return;
        BigDecimal paidByPoints = BigDecimal.ZERO;
        for (Payment p : saved.getPayments()) {
            if (Tenders.LOYALTY.equals(p.getMethod())) paidByPoints = paidByPoints.add(p.getAmount());
        }
        BigDecimal base = OrderCalculator.total(saved).subtract(paidByPoints);
        int earned = 0;
        if (base.signum() > 0) {
            long steps = base.divideToIntegralValue(s.getSpendAmount()).longValue();
            long pts = steps * s.getPointsEarned();
            earned = (int) Math.min(pts, 100000000L);
        }
        Customer locked = customers.lockById(customer.getId()).orElse(customer);
        int balance = balanceOf(locked.getId());
        if (earned > 0) {
            balance += earned;
            write(locked.getId(), LoyaltyTxnType.EARNED, earned, balance, saved.getId(), null, null);
        }
        saved.setLoyaltyEarned(earned);
        saved.setLoyaltyBalance(balance);
    }

    // ------------------------------------------------------------------ returns

    /**
     * Gives back the points a sale was (partly) paid with, in the same share as the refund is of the sale. {@code share} is the money part of this return that points paid for
     * (already worked out by the caller) and {@code pointsBack} the points that equals. Returns the points returned.
     */
    @Transactional
    public int refundPoints(SaleOrder order, int pointsBack, Long returnId) {
        if (pointsBack <= 0) return 0;
        Customer c = customers.lockById(order.getCustomer().getId()).orElse(null);
        if (c == null) return 0;
        int have = balanceOf(c.getId());
        write(c.getId(), LoyaltyTxnType.REDEEM_REFUNDED, pointsBack, have + pointsBack, order.getId(), returnId, "Return");
        return pointsBack;
    }

    /**
     * Takes back the points earned on the part of the sale being returned: {@code refundShare} (0..1) of what the sale earned, less what earlier returns already took back, and never
     * more than the customer still has. Returns the points taken.
     */
    @Transactional
    public int reverseEarned(SaleOrder order, BigDecimal refundShare, Long returnId) {
        if (order.getLoyaltyEarned() == null || order.getLoyaltyEarned() <= 0 || order.getCustomer() == null) return 0;
        Customer c = customers.lockById(order.getCustomer().getId()).orElse(null);
        if (c == null) return 0;
        int earned = order.getLoyaltyEarned();
        int alreadyTaken = -txns.sumForOrder(order.getId(), LoyaltyTxnType.EARN_REVERSED).intValue();
        int wanted = BigDecimal.valueOf(earned).multiply(refundShare.min(BigDecimal.ONE)).setScale(0, RoundingMode.HALF_UP).intValue();
        int take = Math.min(wanted, earned - alreadyTaken);
        int have = balanceOf(c.getId());
        take = Math.min(take, Math.max(have, 0));
        if (take <= 0) return 0;
        write(c.getId(), LoyaltyTxnType.EARN_REVERSED, -take, have - take, order.getId(), returnId, "Return");
        return take;
    }

    // ------------------------------------------------------------------ helpers

    private void write(Long customerId, LoyaltyTxnType type, int points, int balanceAfter, Long orderId, Long returnId, String note) {
        LoyaltyTxn t = new LoyaltyTxn();
        t.setCustomerId(customerId);
        t.setType(type);
        t.setPoints(points);
        t.setBalanceAfter(balanceAfter);
        t.setOrderId(orderId);
        t.setReturnId(returnId);
        t.setNote(note);
        txns.save(t);
    }

    private LoyaltySetting setting() {
        return settings.findById(1L).orElseGet(() -> settings.save(new LoyaltySetting()));
    }

    private static BigDecimal worth(int points, LoyaltySetting s) {
        return BigDecimal.valueOf(points).multiply(s.getPointValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private static Settings toDto(LoyaltySetting s) {
        return new Settings(s.isEnabled(), s.getSpendAmount(), s.getPointsEarned(), s.getPointValue(), s.getMinRedeemPoints(), s.getMaxRedeemPercent());
    }

    private static String trim(String note) {
        if (note == null || note.isBlank()) return null;
        String n = note.trim();
        return n.length() > 300 ? n.substring(0, 300) : n;
    }
}
