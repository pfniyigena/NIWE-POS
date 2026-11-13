package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.salesorder.SalesOrderReturnService;
import com.posapp.salesorder.SalesOrderService;
import com.posapp.salesorder.dto.ProcessSalesOrderReturnRequest;
import com.posapp.salesorder.dto.SalesOrderDto;
import com.posapp.salesorder.dto.SalesOrderLineDto;
import com.posapp.salesorder.dto.SalesOrderReturnDto;
import com.posapp.settings.StoreSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Returns after completion. The customer asks (order code plus the phone number of the order, or a signed-in account that holds the order), staff
 * approve or refuse, and processing does the real work: the goods go back in stock through the existing sale order return (so the books, the reports
 * and the credit figures see it like any other return) and the money is given back the way the customer paid, by the payment provider or by hand for
 * money paid online, in cash for cash on delivery. The customer is told at every step by e-mail and, if they asked for texts, by text.
 */
@Service
public class OnlineReturnService {

    private final ShopLoyaltyService loyalty;

    private static final Set<String> HOLDING = Set.of("REQUESTED", "APPROVED", "REFUNDED");
    private static final long HOUR = 3_600_000L;

    private final OnlineReturnRepository returns;
    private final OnlineOrderRepository orders;
    private final OnlineStoreService storeService;
    private final SalesOrderService salesOrderService;
    private final SalesOrderReturnService salesOrderReturns;
    private final OnlinePaymentService payments;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public OnlineReturnService(OnlineReturnRepository returns, OnlineOrderRepository orders, OnlineStoreService storeService, SalesOrderService salesOrderService,
                               SalesOrderReturnService salesOrderReturns, OnlinePaymentService payments, MailService mailService,
                               StoreSettingsService storeSettingsService, AuditLogService auditLogService, ShopLoyaltyService loyalty) {
        this.loyalty = loyalty;
        this.returns = returns;
        this.orders = orders;
        this.storeService = storeService;
        this.salesOrderService = salesOrderService;
        this.salesOrderReturns = salesOrderReturns;
        this.payments = payments;
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

    // ------------------------------------------------------------------ rules

    /** When the order was completed (the last COMPLETED step of its history), or null. */
    static Instant completedAt(OnlineOrder o) {
        Instant at = null;
        for (OnlineOrderEvent e : o.getEvents()) if ("COMPLETED".equals(e.getStatus())) at = e.getCreatedAt();
        return at;
    }

    /** Why this order cannot be returned now, or null when it can. */
    static String whyNot(OnlineOrder o, int windowDays, Instant now) {
        if (windowDays <= 0) return I18n.t("Returns are not taken through the shop. Please contact us.");
        Instant done = completedAt(o);
        if (!"COMPLETED".equals(o.getStatus()) || o.getSalesOrderId() == null || done == null) return I18n.t("Only a completed order can be returned.");
        if (now.isAfter(done.plus(Duration.ofDays(windowDays)))) return I18n.t("The return period of {0} days for this order has ended.", windowDays);
        return null;
    }

    private static BigDecimal paidFor(OnlineOrderLine l) {
        BigDecimal disc = l.getDiscountAmount() == null ? BigDecimal.ZERO : l.getDiscountAmount();
        return l.getLineTotal().subtract(disc);
    }

    /** Per order line: how much of it other returns (asked, approved or done) already hold, in quantity and in money. */
    private static Map<Long, BigDecimal[]> held(List<OnlineReturn> all) {
        Map<Long, BigDecimal[]> out = new HashMap<>();
        for (OnlineReturn r : all) {
            if (!HOLDING.contains(r.getStatus())) continue;
            for (OnlineReturnLine l : r.getLines()) {
                BigDecimal[] h = out.computeIfAbsent(l.getOnlineOrderLineId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                h[0] = h[0].add(l.getQuantity());
                h[1] = h[1].add(l.getAmount());
            }
        }
        return out;
    }

    private static PublicReturn publicView(OnlineReturn r) {
        return new PublicReturn(r.getCode(), r.getStatus(), r.getReason(), r.getStaffNote(), r.getRefundAmount(), r.getRefundMethod(), r.getRefundReference(),
                r.getCreatedAt(), r.getDecidedAt(), r.getRefundedAt(),
                r.getLines().stream().map(l -> new PublicReturnLine(l.getName(), l.getQuantity(), l.getAmount())).toList());
    }

    // ------------------------------------------------------------------ the customer

    /** What can still be returned on an order and the returns already asked. The code is the key, like following an order. */
    @Transactional
    public ReturnOptions options(String code, String ip) {
        OnlineStoreSettings s = openSettings();
        throttle("t:" + ip, Math.max(60, s.getMaxPerIpPerHour() * 5));
        OnlineOrder o = orders.findByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound(I18n.t("We could not find that order. Check the code.")));
        List<OnlineReturn> all = returns.findByOnlineOrderIdOrderByIdAsc(o.getId());
        String why = whyNot(o, s.getReturnWindowDays(), Instant.now());
        Map<Long, BigDecimal[]> held = held(all);
        List<ReturnOptionLine> lines = new ArrayList<>();
        for (OnlineOrderLine l : o.getLines()) {
            BigDecimal[] h = held.getOrDefault(l.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal left = l.getQuantity().subtract(h[0]).max(BigDecimal.ZERO);
            BigDecimal unit = paidFor(l).divide(l.getQuantity(), 2, RoundingMode.HALF_UP);
            lines.add(new ReturnOptionLine(l.getId(), l.getName(), l.getQuantity(), unit, left));
        }
        boolean anything = lines.stream().anyMatch(l -> l.returnable().signum() > 0);
        if (why == null && !anything) why = I18n.t("Everything on this order has already been returned or asked back.");
        Instant done = completedAt(o);
        Instant last = done == null || s.getReturnWindowDays() <= 0 ? null : done.plus(Duration.ofDays(s.getReturnWindowDays()));
        return new ReturnOptions(why == null, why, s.getReturnWindowDays(), last, lines, all.stream().map(OnlineReturnService::publicView).toList());
    }

    /**
     * The customer asks to return some of the goods. Needs the phone number of the order unless a signed-in customer's account holds it. Counts the
     * quantities against what other returns hold, so the same goods cannot be asked back twice.
     */
    @Transactional
    public PublicReturn request(String code, ReturnRequest in, String ip, Long shopCustomerId) {
        OnlineStoreSettings s = openSettings();
        throttle("r:" + ip, 20);
        OnlineOrder o = orders.lockByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound(I18n.t("We could not find that order. Check the code.")));
        boolean owner = shopCustomerId != null && shopCustomerId.equals(o.getShopCustomerId());
        if (!owner && (key(in.phone()).length() < 6 || !key(in.phone()).equals(o.getPhoneKey()))) {
            throw ApiException.badRequest(I18n.t("The phone number does not match this order."));
        }
        String why = whyNot(o, s.getReturnWindowDays(), Instant.now());
        if (why != null) throw ApiException.badRequest(why);
        String reason = in.reason() == null ? "" : in.reason().trim();
        if (reason.length() < 3) throw ApiException.badRequest(I18n.t("Tell us why you are returning the goods."));

        List<OnlineReturn> all = returns.findByOnlineOrderIdOrderByIdAsc(o.getId());
        Map<Long, BigDecimal[]> held = held(all);
        Map<Long, BigDecimal> wanted = new LinkedHashMap<>();
        for (ReturnLineRequest l : in.lines()) {
            if (l.quantity() == null || l.quantity().signum() <= 0) throw ApiException.badRequest(I18n.t("Enter a quantity greater than zero."));
            wanted.merge(l.lineId(), l.quantity(), BigDecimal::add);
        }
        OnlineReturn r = new OnlineReturn();
        r.setOnlineOrder(o);
        r.setReason(reason.length() > 300 ? reason.substring(0, 300) : reason);
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> w : wanted.entrySet()) {
            OnlineOrderLine line = o.getLines().stream().filter(x -> x.getId().equals(w.getKey())).findFirst()
                    .orElseThrow(() -> ApiException.badRequest(I18n.t("That item is not on this order.")));
            BigDecimal[] h = held.getOrDefault(line.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal left = line.getQuantity().subtract(h[0]);
            if (w.getValue().compareTo(left) > 0) {
                throw ApiException.badRequest(I18n.t("You can return at most {0} of {1}.", left.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString(), line.getName()));
            }
            BigDecimal paid = paidFor(line);
            BigDecimal amount = w.getValue().compareTo(left) == 0
                    ? paid.subtract(h[1])
                    : paid.multiply(w.getValue()).divide(line.getQuantity(), 2, RoundingMode.HALF_UP).min(paid.subtract(h[1]));
            amount = amount.max(BigDecimal.ZERO);
            OnlineReturnLine rl = new OnlineReturnLine();
            rl.setOnlineReturn(r);
            rl.setOnlineOrderLineId(line.getId());
            rl.setProductId(line.getProductId());
            rl.setName(line.getName());
            rl.setQuantity(w.getValue());
            rl.setAmount(amount);
            r.getLines().add(rl);
            total = total.add(amount);
        }
        r.setRefundAmount(total);
        r.setCode(o.getCode() + "-R" + (all.size() + 1));
        returns.save(r);
        event(o, I18n.t("Return {0} asked: {1}", r.getCode(), OnlineStoreService.money(total)), "customer");
        orders.save(o);
        auditLogService.record("ONLINE_RETURN_REQUESTED", "Return " + r.getCode() + " asked for online order " + o.getCode() + " (" + OnlineStoreService.money(total) + ")", "OnlineReturn", r.getId());
        OnlineMessages.send(mailService, storeSettingsService, o, OnlineMessages.m("Your return request {0}", r.getCode()),
                OnlineMessages.m("We received your return request {0} for order {1}. We will answer as soon as we can.", r.getCode(), o.getCode()));
        OnlineMessages.notifyShopNote(mailService, storeSettingsService, s, I18n.t("New return request {0}", r.getCode()),
                I18n.t("{0} asked to return goods from order {1} (worth {2}): {3}. Open Online Store > Returns.", o.getCustomerName(), o.getCode(), OnlineStoreService.money(total), r.getReason()));
        return publicView(r);
    }

    // ------------------------------------------------------------------ staff

    @Transactional(readOnly = true)
    public List<ReturnDto> list(String q, String status) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return returns.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> wanted == null || (wanted.equals("OPEN") ? r.getStatus().equals("REQUESTED") || r.getStatus().equals("APPROVED") : r.getStatus().equals(wanted)))
                .filter(r -> needle.isEmpty() || r.getCode().toLowerCase(Locale.ROOT).contains(needle)
                        || r.getOnlineOrder().getCode().toLowerCase(Locale.ROOT).contains(needle)
                        || r.getOnlineOrder().getCustomerName().toLowerCase(Locale.ROOT).contains(needle)
                        || r.getOnlineOrder().getCustomerPhone().contains(needle))
                .map(OnlineReturnService::dto).toList();
    }

    @Transactional(readOnly = true)
    public ReturnDto get(Long id) {
        return dto(returns.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Return not found"))));
    }

    /** How many returns wait for a decision and how many are approved and wait for the goods and the refund. */
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        return Map.of("returnsRequested", returns.countByStatus("REQUESTED"), "returnsToRefund", returns.countByStatus("APPROVED"));
    }

    private OnlineReturn lock(Long id, String... allowed) {
        OnlineReturn r = returns.lockById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Return not found")));
        for (String a : allowed) if (r.getStatus().equals(a)) return r;
        throw ApiException.badRequest(I18n.t("This return is {0}, so that is not possible now", r.getStatus().toLowerCase(Locale.ROOT)));
    }

    @Transactional
    public ReturnDto approve(Long id, String note) {
        OnlineReturn r = lock(id, "REQUESTED");
        OnlineOrder o = r.getOnlineOrder();
        r.setStatus("APPROVED");
        r.setStaffNote(clean(note));
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(user());
        returns.save(r);
        event(o, I18n.t("Return {0} approved", r.getCode()), user());
        orders.save(o);
        auditLogService.record("ONLINE_RETURN_APPROVED", "Approved return " + r.getCode() + " of online order " + o.getCode(), "OnlineReturn", r.getId());
        OnlineStoreSettings s = storeService.settings();
        OnlineMessages.Msg text = OnlineMessages.m("Your return {0} for order {1} is approved. Please bring the goods back to us.", r.getCode(), o.getCode())
                .plus((r.getStaffNote() == null ? "" : " " + r.getStaffNote()) + (s.getPickupAddress() == null || s.getPickupAddress().isBlank() ? "" : " " + s.getPickupAddress()));
        OnlineMessages.send(mailService, storeSettingsService, o, OnlineMessages.m("Your return {0} is approved", r.getCode()), text);
        return dto(r);
    }

    @Transactional
    public ReturnDto refuse(Long id, String note) {
        String why = clean(note);
        if (why == null) throw ApiException.badRequest(I18n.t("Write the reason for refusing the return."));
        OnlineReturn r = lock(id, "REQUESTED", "APPROVED");
        OnlineOrder o = r.getOnlineOrder();
        r.setStatus("REFUSED");
        r.setStaffNote(why);
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(user());
        returns.save(r);
        event(o, I18n.t("Return {0} refused", r.getCode()), user());
        orders.save(o);
        auditLogService.record("ONLINE_RETURN_REFUSED", "Refused return " + r.getCode() + " of online order " + o.getCode() + ": " + why, "OnlineReturn", r.getId());
        OnlineMessages.send(mailService, storeSettingsService, o, OnlineMessages.m("Your return {0}", r.getCode()),
                OnlineMessages.m("Sorry, we cannot accept your return {0} for order {1}: {2}", r.getCode(), o.getCode(), why));
        return dto(r);
    }

    /**
     * The goods are back: puts them in stock through a sale order return (stock, revenue and credit figures follow the existing mechanism) and gives the
     * money back the way the customer paid. All or nothing: if the stock side or the refund fails, nothing is recorded.
     */
    @Transactional
    public ReturnDto process(Long id, ReturnProcess in) {
        OnlineReturn r = lock(id, "APPROVED");
        OnlineOrder o = r.getOnlineOrder();
        if (o.getSalesOrderId() == null) throw ApiException.badRequest(I18n.t("This order has no sale order"));

        // The stock side: the quantities go onto the sale order's lines of the same product, oldest first (a coupon can split a product in two lines).
        SalesOrderDto so = salesOrderService.getById(o.getSalesOrderId());
        Map<Long, BigDecimal> already = salesOrderReturns.returnedQuantitiesForOrder(so.id());
        Map<Long, BigDecimal> free = new LinkedHashMap<>();
        for (SalesOrderLineDto l : so.lines()) free.put(l.id(), l.quantityFulfilled().subtract(already.getOrDefault(l.id(), BigDecimal.ZERO)));
        Map<Long, BigDecimal> give = new LinkedHashMap<>();
        for (OnlineReturnLine rl : r.getLines()) {
            BigDecimal need = rl.getQuantity();
            for (SalesOrderLineDto l : so.lines()) {
                if (need.signum() <= 0) break;
                if (!rl.getProductId().equals(l.productId())) continue;
                BigDecimal room = free.get(l.id());
                if (room == null || room.signum() <= 0) continue;
                BigDecimal take = need.min(room);
                free.put(l.id(), room.subtract(take));
                give.merge(l.id(), take, BigDecimal::add);
                need = need.subtract(take);
            }
            if (need.signum() > 0) throw ApiException.badRequest(I18n.t("{0} was already returned on the sale order, so this return cannot be processed.", rl.getName()));
        }
        ProcessSalesOrderReturnRequest req = new ProcessSalesOrderReturnRequest();
        req.setSalesOrderId(so.id());
        req.setReason(I18n.t("Online return {0}: {1}", r.getCode(), r.getReason()));
        req.setLocationId(in == null ? null : in.locationId());
        List<ProcessSalesOrderReturnRequest.ReturnLineEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> g : give.entrySet()) {
            ProcessSalesOrderReturnRequest.ReturnLineEntry e = new ProcessSalesOrderReturnRequest.ReturnLineEntry();
            e.setLineId(g.getKey());
            e.setQuantity(g.getValue());
            entries.add(e);
        }
        req.setLines(entries);
        SalesOrderReturnDto done = salesOrderReturns.processReturn(req);

        // The money side, by how the customer paid.
        BigDecimal amount = r.getRefundAmount();
        String method;
        String ref;
        String note = in == null ? null : clean(in.note());
        if ("ONLINE".equals(o.getPaymentMethod())) {
            BigDecimal left = (o.getPaidAmount() == null ? BigDecimal.ZERO : o.getPaidAmount()).subtract(o.getRefundedTotal() == null ? BigDecimal.ZERO : o.getRefundedTotal());
            amount = amount.min(left);
            if (amount.signum() <= 0) throw ApiException.badRequest(I18n.t("There is nothing left to refund on this order"));
            boolean manual = in != null && in.manual();
            ref = payments.refundPart(o, amount, manual, in == null ? null : in.reference(), I18n.t("Return {0}", r.getCode()));
            method = manual ? "MANUAL" : "PROVIDER";
        } else {
            method = "CASH";
            ref = in == null ? null : clean(in.reference());
        }
        r.setStatus("REFUNDED");
        r.setRefundAmount(amount);
        r.setRefundMethod(method);
        r.setRefundReference(ref);
        r.setRefundedAt(Instant.now());
        r.setRefundedBy(user());
        r.setSalesOrderReturnId(done.id());
        r.setSalesOrderReturnNumber(done.returnNumber());
        r.setLocationId(in == null ? null : in.locationId());
        if (note != null) r.setStaffNote(r.getStaffNote() == null ? note : r.getStaffNote() + " | " + note);
        if (r.getStaffNote() != null && r.getStaffNote().length() > 300) r.setStaffNote(r.getStaffNote().substring(0, 300));
        returns.save(r);
        event(o, I18n.t("Return {0} done: {1} given back", r.getCode(), OnlineStoreService.money(amount)) + (method.equals("CASH") ? " (" + I18n.t("cash") + ")" : ""), user());
        loyalty.afterReturn(o, r);
        orders.save(o);
        auditLogService.record("ONLINE_RETURN_REFUNDED", "Processed return " + r.getCode() + " of online order " + o.getCode() + " as " + done.returnNumber()
                + ", " + OnlineStoreService.money(amount) + " (" + method + ")", "OnlineReturn", r.getId());
        OnlineMessages.send(mailService, storeSettingsService, o, OnlineMessages.m("Your refund for return {0}", r.getCode()), method.equals("CASH")
                ? OnlineMessages.m("We handed back {0} in cash for your return {1} of order {2}. Thank you.", OnlineStoreService.money(amount), r.getCode(), o.getCode())
                : OnlineMessages.m("We refunded {0} for your return {1} of order {2}. Reference: {3}. It may take a few days to show on your account.",
                        OnlineStoreService.money(amount), r.getCode(), o.getCode(), ref));
        return dto(r);
    }

    // ------------------------------------------------------------------ helpers

    private static String clean(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    private void event(OnlineOrder o, String note, String by) {
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus(o.getStatus());
        ev.setNote(note == null || note.length() <= 200 ? note : note.substring(0, 200));
        ev.setByUser(by);
        o.getEvents().add(ev);
    }

    static ReturnDto dto(OnlineReturn r) {
        OnlineOrder o = r.getOnlineOrder();
        return new ReturnDto(r.getId(), r.getCode(), o.getId(), o.getCode(), o.getCustomerName(), o.getCustomerPhone(), o.getCustomerEmail(), r.getStatus(),
                r.getReason(), r.getStaffNote(), r.getRefundAmount(), r.getRefundMethod(), r.getRefundReference(), o.getPaymentMethod(), o.getPaymentStatus(),
                r.getCreatedAt(), r.getDecidedBy(), r.getDecidedAt(), r.getRefundedBy(), r.getRefundedAt(), r.getSalesOrderReturnNumber(),
                r.getLines().stream().map(l -> new ReturnLineDto(l.getOnlineOrderLineId(), l.getName(), l.getQuantity(), l.getAmount())).toList());
    }
}
