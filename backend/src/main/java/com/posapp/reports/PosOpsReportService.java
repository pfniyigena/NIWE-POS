package com.posapp.reports;

import com.posapp.branch.BranchService;
import com.posapp.order.OrderCalculator;
import com.posapp.order.OrderLine;
import com.posapp.order.OrderRepository;
import com.posapp.order.OrderStatus;
import com.posapp.order.Payment;
import com.posapp.order.SaleOrder;
import com.posapp.register.PosExceptionService;
import com.posapp.register.RegisterSession;
import com.posapp.register.RegisterSessionRepository;
import com.posapp.register.dto.PosExceptionDto;
import com.posapp.reports.dto.PosOpsRows.*;
import com.posapp.returns.SalesReturn;
import com.posapp.returns.SalesReturnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * POS operations reports: how the tills and the people on them performed. A sale that was later voided stays in "sales" and shows again as a refund, so
 * net sales are what was kept; a return counts against the cashier who made the original sale.
 */
@Service
public class PosOpsReportService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OrderRepository orderRepository;
    private final SalesReturnRepository returnRepository;
    private final RegisterSessionRepository sessionRepository;
    private final PosExceptionService exceptionService;
    private final BranchService branchService;

    public PosOpsReportService(OrderRepository orderRepository, SalesReturnRepository returnRepository, RegisterSessionRepository sessionRepository,
                               PosExceptionService exceptionService, BranchService branchService) {
        this.orderRepository = orderRepository;
        this.returnRepository = returnRepository;
        this.sessionRepository = sessionRepository;
        this.exceptionService = exceptionService;
        this.branchService = branchService;
    }

    private static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return whole == null || whole.signum() == 0 ? BigDecimal.ZERO.setScale(2) : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private List<SaleOrder> orders(Instant from, Instant to, Long registerId, Long branchId) {
        List<SaleOrder> all = orderRepository.findCompletedForReport(OrderStatus.COMPLETED, from, to, registerId);
        if (branchId == null) return all;
        Long hq = branchService.headquartersId();
        return all.stream().filter(o -> (o.getBranchId() != null ? o.getBranchId() : hq).equals(branchId)).toList();
    }

    private List<SalesReturn> returns(Instant from, Instant to, Long registerId, Long branchId) {
        Long hq = branchId == null ? null : branchService.headquartersId();
        return returnRepository.findByReturnDateBetweenOrderByReturnDateDesc(from, to).stream()
                .filter(r -> r.getOrder() != null)
                .filter(r -> registerId == null || (r.getOrder().getSession() != null && r.getOrder().getSession().getRegister() != null && registerId.equals(r.getOrder().getSession().getRegister().getId())))
                .filter(r -> branchId == null || (r.getOrder().getBranchId() != null ? r.getOrder().getBranchId() : hq).equals(branchId))
                .toList();
    }

    private static String cashierOf(SaleOrder o) {
        var cs = o.getCashierSession();
        var c = cs != null ? cs.getCashier() : (o.getSession() != null ? o.getSession().getCashier() : null);
        return c == null ? "—" : c.getFullName();
    }

    /** What the cashier's discounts and promotions took off a sale (shelf price less what was charged, before tax). */
    private static BigDecimal discountOf(SaleOrder o) {
        BigDecimal shelf = BigDecimal.ZERO;
        BigDecimal after = BigDecimal.ZERO;
        for (OrderLine l : o.getLines()) {
            shelf = shelf.add(l.getUnitPrice().multiply(l.getQuantity()));
            after = after.add(OrderCalculator.lineTotal(l));
        }
        return shelf.subtract(after).max(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<CashierRow> byCashier(Instant from, Instant to, Long registerId, Long branchId) {
        class Acc {
            Set<Long> shifts = new HashSet<>();
            int sales, refunds, voids;
            BigDecimal items = BigDecimal.ZERO;
            BigDecimal total = BigDecimal.ZERO, discounts = BigDecimal.ZERO, refundTotal = BigDecimal.ZERO;
        }
        Map<String, Acc> by = new LinkedHashMap<>();
        for (SaleOrder o : orders(from, to, registerId, branchId)) {
            Acc a = by.computeIfAbsent(cashierOf(o), k -> new Acc());
            if (o.getSession() != null) a.shifts.add(o.getSession().getId());
            a.sales++;
            a.items = a.items.add(OrderCalculator.totalQuantity(o));
            a.total = a.total.add(OrderCalculator.total(o));
            a.discounts = a.discounts.add(discountOf(o));
            if (o.getVoidedAt() != null) a.voids++;
        }
        for (SalesReturn r : returns(from, to, registerId, branchId)) {
            Acc a = by.computeIfAbsent(cashierOf(r.getOrder()), k -> new Acc());
            a.refunds++;
            a.refundTotal = a.refundTotal.add(r.getRefundTotal() == null ? BigDecimal.ZERO : r.getRefundTotal());
        }
        List<CashierRow> rows = new ArrayList<>();
        for (var e : by.entrySet()) {
            Acc a = e.getValue();
            BigDecimal avg = a.sales == 0 ? BigDecimal.ZERO : a.total.divide(BigDecimal.valueOf(a.sales), 2, RoundingMode.HALF_UP);
            rows.add(new CashierRow(e.getKey(), a.shifts.size(), a.sales, a.items, scale(a.total), scale(a.discounts), a.refunds, scale(a.refundTotal), a.voids, scale(a.total.subtract(a.refundTotal)), scale(avg)));
        }
        rows.sort(Comparator.comparing(CashierRow::netSales).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<HourRow> byHour(Instant from, Instant to, Long registerId, Long branchId) {
        ZoneId zone = ZoneId.systemDefault();
        Map<Integer, BigDecimal[]> counts = new TreeMap<>();
        Map<Integer, BigDecimal> totals = new TreeMap<>();
        BigDecimal all = BigDecimal.ZERO;
        for (SaleOrder o : orders(from, to, registerId, branchId)) {
            if (o.getCompletedAt() == null) continue;
            int h = o.getCompletedAt().atZone(zone).getHour();
            BigDecimal[] c = counts.computeIfAbsent(h, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            c[0] = c[0].add(BigDecimal.ONE);
            c[1] = c[1].add(OrderCalculator.totalQuantity(o));
            BigDecimal t = OrderCalculator.total(o);
            totals.merge(h, t, BigDecimal::add);
            all = all.add(t);
        }
        List<HourRow> rows = new ArrayList<>();
        for (var e : counts.entrySet()) {
            BigDecimal t = totals.get(e.getKey());
            rows.add(new HourRow(String.format("%02d:00–%02d:59", e.getKey(), e.getKey()), e.getValue()[0].intValue(), e.getValue()[1], scale(t),
                    scale(t.divide(e.getValue()[0], 2, RoundingMode.HALF_UP)), pct(t, all)));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<PaymentRow> byPayment(Instant from, Instant to, Long registerId, Long branchId) {
        Map<String, BigDecimal> paid = new LinkedHashMap<>();
        Map<String, Integer> paidN = new LinkedHashMap<>();
        Map<String, BigDecimal> refunded = new LinkedHashMap<>();
        Map<String, Integer> refundedN = new LinkedHashMap<>();
        BigDecimal all = BigDecimal.ZERO;
        for (SaleOrder o : orders(from, to, registerId, branchId)) {
            for (Payment p : o.getPayments()) {
                paid.merge(p.getMethod(), p.getAmount(), BigDecimal::add);
                paidN.merge(p.getMethod(), 1, Integer::sum);
                all = all.add(p.getAmount());
            }
        }
        for (SalesReturn r : returns(from, to, registerId, branchId)) {
            String m = r.getRefundMethod() == null ? "—" : r.getRefundMethod();
            // the part given back onto gift cards or as points is not a refund of the payment method named
            BigDecimal cash = (r.getRefundTotal() == null ? BigDecimal.ZERO : r.getRefundTotal())
                    .subtract(r.getGiftCardRefund() == null ? BigDecimal.ZERO : r.getGiftCardRefund())
                    .subtract(r.getLoyaltyRefund() == null ? BigDecimal.ZERO : r.getLoyaltyRefund()).max(BigDecimal.ZERO);
            refunded.merge(m, cash, BigDecimal::add);
            refundedN.merge(m, 1, Integer::sum);
        }
        Set<String> methods = new java.util.TreeSet<>(paid.keySet());
        methods.addAll(refunded.keySet());
        List<PaymentRow> rows = new ArrayList<>();
        for (String m : methods) {
            BigDecimal a = paid.getOrDefault(m, BigDecimal.ZERO);
            BigDecimal rf = refunded.getOrDefault(m, BigDecimal.ZERO);
            rows.add(new PaymentRow(m, paidN.getOrDefault(m, 0), scale(a), refundedN.getOrDefault(m, 0), scale(rf), scale(a.subtract(rf)), pct(a, all)));
        }
        rows.sort(Comparator.comparing(PaymentRow::amount).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ExceptionRow> exceptions(LocalDate from, LocalDate to, Long registerId) {
        List<ExceptionRow> rows = new ArrayList<>();
        for (PosExceptionDto e : exceptionService.filtered(null, null, registerId, from, to)) {
            String kind = switch (e.type()) {
                case "VOID" -> "Voided sale";
                case "RETURN" -> "Return";
                case "DISCOUNT" -> "Discount";
                case "PRICE_OVERRIDE" -> "Price changed";
                case "REPRINT" -> "Receipt reprint";
                default -> "Dropped cart";
            };
            rows.add(new ExceptionRow(e.at(), kind, e.ref(), e.customer(), e.registerName(), e.by(), e.amount(), e.reason(), e.approvedBy()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ShiftCompareRow> shiftComparison(Instant from, Instant to, Long registerId, Long branchId) {
        Long hq = branchId == null ? null : branchService.headquartersId();
        List<RegisterSession> sessions = sessionRepository.findAllByOrderByOpenedAtDesc().stream()
                .filter(s -> s.getOpenedAt() != null && !s.getOpenedAt().isBefore(from) && !s.getOpenedAt().isAfter(to))
                .filter(s -> registerId == null || (s.getRegister() != null && registerId.equals(s.getRegister().getId())))
                .toList();
        record Calc(RegisterSession s, int sales, BigDecimal items, BigDecimal total, BigDecimal discounts, BigDecimal refunds, int voids) {}
        List<Calc> calcs = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        int counted = 0;
        for (RegisterSession s : sessions) {
            int n = 0, voids = 0;
            BigDecimal items = BigDecimal.ZERO;
            BigDecimal total = BigDecimal.ZERO, disc = BigDecimal.ZERO;
            for (SaleOrder o : orderRepository.findBySessionIdAndStatusOrderByCreatedAtAsc(s.getId(), OrderStatus.COMPLETED)) {
                if (branchId != null && !(o.getBranchId() != null ? o.getBranchId() : hq).equals(branchId)) continue;
                n++;
                items = items.add(OrderCalculator.totalQuantity(o));
                total = total.add(OrderCalculator.total(o));
                disc = disc.add(discountOf(o));
                if (o.getVoidedAt() != null) voids++;
            }
            BigDecimal refunds = BigDecimal.ZERO;
            for (SalesReturn r : returnRepository.findForSession(s.getId())) {
                refunds = refunds.add(r.getRefundTotal() == null ? BigDecimal.ZERO : r.getRefundTotal());
            }
            calcs.add(new Calc(s, n, items, total, disc, refunds, voids));
            if (n > 0) {
                sum = sum.add(total);
                counted++;
            }
        }
        BigDecimal avgShift = counted == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(counted), 2, RoundingMode.HALF_UP);
        List<ShiftCompareRow> rows = new ArrayList<>();
        for (Calc c : calcs) {
            RegisterSession s = c.s();
            BigDecimal avgSale = c.sales() == 0 ? BigDecimal.ZERO : c.total().divide(BigDecimal.valueOf(c.sales()), 2, RoundingMode.HALF_UP);
            BigDecimal perSale = c.sales() == 0 ? BigDecimal.ZERO : c.items().divide(BigDecimal.valueOf(c.sales()), 2, RoundingMode.HALF_UP);
            BigDecimal vs = c.sales() == 0 || avgShift.signum() == 0 ? null : c.total().subtract(avgShift).multiply(HUNDRED).divide(avgShift, 2, RoundingMode.HALF_UP);
            rows.add(new ShiftCompareRow(s.getId(), s.getZNumber(), s.getRegister() == null ? "—" : s.getRegister().getName(), s.getCashier() == null ? "—" : s.getCashier().getFullName(),
                    s.getOpenedAt(), s.getClosedAt(), c.sales(), scale(c.total()), scale(avgSale), perSale, scale(c.discounts()), scale(c.refunds()), c.voids(),
                    s.getExpectedCash() == null ? null : scale(s.getExpectedCash()), s.getCountedCash() == null ? null : scale(s.getCountedCash()),
                    s.getCashVariance() == null ? null : scale(s.getCashVariance()), vs));
        }
        return rows;
    }
}
