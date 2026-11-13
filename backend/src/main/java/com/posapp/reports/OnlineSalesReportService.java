package com.posapp.reports;

import com.posapp.i18n.I18n;
import com.posapp.reports.dto.OnlineSalesReportRows.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Online sales reports. A sale counts on the day its online order was COMPLETED (the goods were handed over and the invoice issued); the money
 * is what the invoice says. Orders that were not completed (open, waiting for payment, never paid, cancelled, rejected) are shown apart in
 * {@link #notSales}, by the day they were placed. Returns and refunds are shown on the day they were refunded.
 */
@Service
public class OnlineSalesReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String G_OPEN = "Open (being handled)";
    private static final String G_WAIT = "Waiting for online payment";
    private static final String G_NEVER = "Never paid (cancelled)";
    private static final String G_CANC = "Cancelled";
    private static final String G_REJ = "Rejected";

    @PersistenceContext
    private EntityManager em;

    // ------------------------------------------------------------------ reading the data

    private record Sale(Instant at, String code, String customer, String delivery, String paymentMethod, String channel, String coupon, BigDecimal goods,
                        BigDecimal discount, BigDecimal fee, BigDecimal feeWaived, BigDecimal orderTotal, String invoice, BigDecimal invoiced, String invoiceStatus,
                        BigDecimal paid, BigDecimal refunded, long invoiceId, BigDecimal points) {
    }

    private static Instant instant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof OffsetDateTime d) return d.toInstant();
        if (o instanceof ZonedDateTime z) return z.toInstant();
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof java.util.Date d) return d.toInstant();
        throw new IllegalStateException("Not a time: " + o.getClass());
    }

    private static BigDecimal bd(Object o) {
        if (o == null) return ZERO;
        if (o instanceof BigDecimal b) return b;
        return new BigDecimal(String.valueOf(o));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Instant start(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private static LocalDate day(Instant at) {
        return at.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    @SuppressWarnings("unchecked")
    private List<Sale> sales(LocalDate from, LocalDate to) {
        List<Object[]> rows = em.createNativeQuery("""
                        select e.done_at, o.code, o.customer_name, o.delivery_method, o.payment_method, o.payment_channel, o.coupon_code,
                               o.subtotal, o.discount_amount, o.delivery_fee, o.fee_waived, o.total,
                               o.invoice_number, i.total_amount, i.status,
                               coalesce((select sum(a.amount_applied) from customer_payment_allocation a where a.invoice_id = i.id), 0),
                               o.refunded_total, coalesce(i.id, 0), o.loyalty_discount
                          from online_order o
                          join (select online_order_id, min(created_at) as done_at from online_order_event where status = 'COMPLETED' group by online_order_id) e
                            on e.online_order_id = o.id
                          left join invoice i on i.id = o.invoice_id
                         where e.done_at >= :s and e.done_at < :e
                         order by e.done_at, o.id
                        """)
                .setParameter("s", start(from)).setParameter("e", start(to.plusDays(1))).getResultList();
        List<Sale> out = new ArrayList<>();
        for (Object[] r : rows) {
            boolean voided = "VOID".equals(str(r[14]));
            out.add(new Sale(instant(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]), str(r[6]), bd(r[7]), bd(r[8]), bd(r[9]), bd(r[10]), bd(r[11]),
                    str(r[12]), voided ? ZERO : bd(r[13]), str(r[14]), voided ? ZERO : bd(r[15]), bd(r[16]), ((Number) r[17]).longValue(), bd(r[18])));
        }
        return out;
    }

    private record Refund(Instant at, BigDecimal amount) {
    }

    @SuppressWarnings("unchecked")
    private List<Refund> refunds(LocalDate from, LocalDate to) {
        List<Object[]> rows = em.createNativeQuery("""
                        select r.refunded_at, r.refund_amount from online_return r
                         where r.status = 'REFUNDED' and r.refunded_at >= :s and r.refunded_at < :e
                         order by r.refunded_at
                        """)
                .setParameter("s", start(from)).setParameter("e", start(to.plusDays(1))).getResultList();
        List<Refund> out = new ArrayList<>();
        for (Object[] r : rows) out.add(new Refund(instant(r[0]), bd(r[1])));
        return out;
    }

    // ------------------------------------------------------------------ reports

    /** Sales and refunds day by day. */
    @Transactional(readOnly = true)
    public List<DayRow> daily(LocalDate from, LocalDate to) {
        record Acc(int[] n, BigDecimal[] m) {
        }
        // n: orders, delivery, pickup, returns; m: goods, discounts, fees, invoiced, refunded
        Map<LocalDate, Acc> days = new TreeMap<>();
        for (Sale s : sales(from, to)) {
            Acc a = days.computeIfAbsent(day(s.at()), k -> new Acc(new int[4], new BigDecimal[]{ZERO, ZERO, ZERO, ZERO, ZERO}));
            a.n[0]++;
            if ("DELIVERY".equals(s.delivery())) a.n[1]++;
            else a.n[2]++;
            a.m[0] = a.m[0].add(s.goods());
            a.m[1] = a.m[1].add(s.discount());
            a.m[2] = a.m[2].add(s.fee());
            a.m[3] = a.m[3].add(s.invoiced());
        }
        for (Refund r : refunds(from, to)) {
            Acc a = days.computeIfAbsent(day(r.at()), k -> new Acc(new int[4], new BigDecimal[]{ZERO, ZERO, ZERO, ZERO, ZERO}));
            a.n[3]++;
            a.m[4] = a.m[4].add(r.amount());
        }
        List<DayRow> out = new ArrayList<>();
        days.forEach((d, a) -> out.add(new DayRow(d, a.n[0], a.n[1], a.n[2], a.m[0], a.m[1], a.m[2], a.m[3], a.n[3], a.m[4], a.m[3].subtract(a.m[4]))));
        return out;
    }

    /** Every completed order with its invoice, so the totals can be checked one by one. */
    @Transactional(readOnly = true)
    public List<OrderRow> orders(LocalDate from, LocalDate to) {
        List<OrderRow> out = new ArrayList<>();
        for (Sale s : sales(from, to)) {
            String time = s.at().atZone(ZoneId.systemDefault()).toLocalTime().withNano(0).toString();
            out.add(new OrderRow(day(s.at()), time.length() > 5 ? time.substring(0, 5) : time, s.code(), s.customer(), deliveryLabel(s.delivery()), payLabel(s), s.coupon(), s.goods(),
                    s.discount(), s.fee(), s.orderTotal(), s.invoice(), s.invoiced(), s.paid(), s.refunded()));
        }
        return out;
    }

    private static String nice(String code) {
        if (code == null || code.isBlank()) return "";
        String t = code.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** A payment method or channel the way people say it. */
    private static String method(String code) {
        if (code == null || code.isBlank()) return I18n.t("Unknown");
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "CASH" -> I18n.t("Cash");
            case "MOBILE_MONEY" -> I18n.t("Mobile money");
            case "CARD" -> I18n.t("Card");
            default -> nice(code);
        };
    }

    private static String deliveryLabel(String code) {
        return "DELIVERY".equals(code) ? I18n.t("Delivery") : I18n.t("Pick-up");
    }

    private static String payLabel(Sale s) {
        return "ONLINE".equals(s.paymentMethod()) ? (s.channel() == null ? I18n.t("Paid online") : I18n.t("Online: {0}", method(s.channel()))) : I18n.t("On delivery / pick-up");
    }

    /** How the invoices of the orders completed in the period were paid, by method, plus what has not been collected. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<PaymentRow> payments(LocalDate from, LocalDate to) {
        List<Sale> sales = sales(from, to);
        BigDecimal invoiced = sales.stream().map(Sale::invoiced).reduce(ZERO, BigDecimal::add);
        List<Object[]> rows = em.createNativeQuery("""
                        select coalesce(cp.method, 'Unknown'), count(*), coalesce(sum(a.amount_applied), 0)
                          from customer_payment_allocation a
                          join customer_payment cp on cp.id = a.payment_id
                          join invoice i on i.id = a.invoice_id and i.status <> 'VOID'
                          join online_order o on o.invoice_id = i.id
                          join (select online_order_id, min(created_at) as done_at from online_order_event where status = 'COMPLETED' group by online_order_id) e
                            on e.online_order_id = o.id
                         where e.done_at >= :s and e.done_at < :e
                         group by coalesce(cp.method, 'Unknown')
                         order by 3 desc, 1
                        """)
                .setParameter("s", start(from)).setParameter("e", start(to.plusDays(1))).getResultList();
        List<PaymentRow> out = new ArrayList<>();
        BigDecimal received = ZERO;
        for (Object[] r : rows) {
            BigDecimal amount = bd(r[2]);
            received = received.add(amount);
            out.add(new PaymentRow(method(str(r[0])), ((Number) r[1]).intValue(), amount));
        }
        BigDecimal open = invoiced.subtract(received);
        if (open.signum() != 0) out.add(new PaymentRow(I18n.t("Not collected yet"), 0, open));
        return out;
    }

    /** Orders placed in the period that are not sales: open, waiting for payment, never paid, cancelled, rejected. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<StatusRow> notSales(LocalDate from, LocalDate to) {
        List<Object[]> rows = em.createNativeQuery("""
                        select o.status, o.payment_method, o.payment_status, o.total, coalesce(o.paid_amount, 0), o.refunded_total
                          from online_order o
                         where o.created_at >= :s and o.created_at < :e and o.status <> 'COMPLETED'
                        """)
                .setParameter("s", start(from)).setParameter("e", start(to.plusDays(1))).getResultList();
        Map<String, BigDecimal[]> groups = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String g : List.of(G_OPEN, G_WAIT, G_NEVER, G_CANC, G_REJ)) {
            groups.put(g, new BigDecimal[]{ZERO, ZERO, ZERO});
            counts.put(g, 0);
        }
        for (Object[] r : rows) {
            String status = str(r[0]);
            String method = str(r[1]);
            String pay = str(r[2]);
            BigDecimal paid = bd(r[4]);
            String g;
            switch (status) {
                case "AWAITING_PAYMENT" -> g = G_WAIT;
                case "REJECTED" -> g = G_REJ;
                case "CANCELLED" -> g = "ONLINE".equals(method) && paid.signum() == 0 && !"PAID".equals(pay) && !"REFUND_DUE".equals(pay) && !"REFUNDED".equals(pay)
                        ? G_NEVER : G_CANC;
                default -> g = G_OPEN;
            }
            BigDecimal[] m = groups.get(g);
            m[0] = m[0].add(bd(r[3]));
            m[1] = m[1].add(paid);
            m[2] = m[2].add(bd(r[5]));
            counts.merge(g, 1, Integer::sum);
        }
        List<StatusRow> out = new ArrayList<>();
        groups.forEach((g, m) -> {
            if (counts.get(g) > 0) out.add(new StatusRow(I18n.t(g), counts.get(g), m[0], m[1], m[2]));
        });
        return out;
    }

    /** What each coupon cost and brought, on orders completed in the period. */
    @Transactional(readOnly = true)
    public List<CouponRow> coupons(LocalDate from, LocalDate to) {
        Map<String, BigDecimal[]> by = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Sale s : sales(from, to)) {
            if (s.coupon() == null || s.coupon().isBlank()) continue;
            BigDecimal[] m = by.computeIfAbsent(s.coupon(), k -> new BigDecimal[]{ZERO, ZERO, ZERO});
            m[0] = m[0].add(s.discount().subtract(s.points()));
            m[1] = m[1].add(s.feeWaived());
            m[2] = m[2].add(s.invoiced());
            counts.merge(s.coupon(), 1, Integer::sum);
        }
        List<CouponRow> out = new ArrayList<>();
        by.forEach((c, m) -> out.add(new CouponRow(c, counts.get(c), m[0], m[1], m[2])));
        out.sort(Comparator.comparing(CouponRow::discounts).reversed().thenComparing(CouponRow::coupon));
        return out;
    }

    /** Best-selling products on orders completed in the period; returns refunded in the period are taken off. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ProductRow> products(LocalDate from, LocalDate to) {
        List<Object[]> sold = em.createNativeQuery("""
                        select l.product_id, max(l.name), sum(l.quantity), sum(l.line_total - l.discount_amount)
                          from online_order_line l
                          join online_order o on o.id = l.online_order_id
                          join (select online_order_id, min(created_at) as done_at from online_order_event where status = 'COMPLETED' group by online_order_id) e
                            on e.online_order_id = o.id
                         where e.done_at >= :s and e.done_at < :e
                         group by l.product_id
                        """)
                .setParameter("s", start(from)).setParameter("e", start(to.plusDays(1))).getResultList();
        List<Object[]> back = em.createNativeQuery("""
                        select rl.product_id, max(rl.name), sum(rl.quantity), sum(rl.amount)
                          from online_return_line rl
                          join online_return r on r.id = rl.online_return_id
                         where r.status = 'REFUNDED' and r.refunded_at >= :s and r.refunded_at < :e
                         group by rl.product_id
                        """)
                .setParameter("s", start(from)).setParameter("e", start(to.plusDays(1))).getResultList();
        Map<Long, BigDecimal[]> m = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (Object[] r : sold) {
            long id = ((Number) r[0]).longValue();
            names.put(id, str(r[1]));
            m.put(id, new BigDecimal[]{bd(r[2]), bd(r[3]), ZERO, ZERO});
        }
        for (Object[] r : back) {
            long id = ((Number) r[0]).longValue();
            names.putIfAbsent(id, str(r[1]));
            BigDecimal[] a = m.computeIfAbsent(id, k -> new BigDecimal[]{ZERO, ZERO, ZERO, ZERO});
            a[2] = bd(r[2]);
            a[3] = bd(r[3]);
        }
        List<ProductRow> out = new ArrayList<>();
        m.forEach((id, a) -> out.add(new ProductRow(names.get(id), a[0], a[2], a[0].subtract(a[2]), a[1], a[3], a[1].subtract(a[3]))));
        out.sort(Comparator.comparing(ProductRow::net).reversed().thenComparing(ProductRow::product));
        return out;
    }
}
