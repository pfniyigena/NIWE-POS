package com.posapp.register;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posapp.common.ApiException;
import com.posapp.giftcards.GiftCardService;
import com.posapp.order.OrderCalculator;
import com.posapp.order.OrderLine;
import com.posapp.order.OrderRepository;
import com.posapp.order.OrderService;
import com.posapp.order.OrderStatus;
import com.posapp.order.Payment;
import com.posapp.order.SaleOrder;
import com.posapp.paymentmethod.PaymentMethodService;
import com.posapp.paymentmethod.dto.PaymentMethodDto;
import com.posapp.register.dto.ShiftReportDto;
import com.posapp.register.dto.ShiftReportDto.*;
import com.posapp.returns.ReturnsService;
import com.posapp.returns.SalesReturn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Builds the X report (live, any time while the shift is open) and the Z report (the same, frozen with a number when the shift is closed).
 * Every figure here is worked out from the shift's sales, returns and cash movements; nothing is stored except the frozen copy.
 */
@Service
public class ShiftReportService {

    private final RegisterSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ReturnsService returnsService;
    private final CashControlService cashControlService;
    private final CashierSessionService cashierSessionService;
    private final PaymentMethodService paymentMethodService;
    private final GiftCardService giftCardService;
    private final ObjectMapper objectMapper;

    public ShiftReportService(RegisterSessionRepository sessionRepository, OrderRepository orderRepository, OrderService orderService, ReturnsService returnsService,
                              CashControlService cashControlService, CashierSessionService cashierSessionService, PaymentMethodService paymentMethodService,
                              GiftCardService giftCardService, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.returnsService = returnsService;
        this.cashControlService = cashControlService;
        this.cashierSessionService = cashierSessionService;
        this.paymentMethodService = paymentMethodService;
        this.giftCardService = giftCardService;
        this.objectMapper = objectMapper;
    }

    /** "X" while the shift is open, "Z" once closed. */
    @Transactional(readOnly = true)
    public String reportType(Long sessionId) {
        return session(sessionId).getStatus() == SessionStatus.OPEN ? "X" : "Z";
    }

    /** The X report: the shift so far. Only for a shift that is still open (a closed one has its Z report). */
    @Transactional(readOnly = true)
    public ShiftReportDto xReport(Long sessionId) {
        RegisterSession s = session(sessionId);
        if (s.getStatus() != SessionStatus.OPEN) throw ApiException.badRequest("That shift is closed; open its Z report instead");
        return build(s, "X");
    }

    /** The Z report of a closed shift, as it was when the shift was closed. {@code print} counts this reading as a printing (the second and later are copies). */
    @Transactional
    public ShiftReportDto zReport(Long sessionId, boolean print) {
        RegisterSession s = session(sessionId);
        if (s.getStatus() != SessionStatus.CLOSED) throw ApiException.badRequest("This shift is still open; the Z report is made when it is closed. Use the X report to look at it meanwhile");
        ShiftReportDto frozen = null;
        if (s.getZSnapshot() != null) {
            try {
                frozen = objectMapper.readValue(s.getZSnapshot(), ShiftReportDto.class);
            } catch (Exception e) {
                frozen = null;
            }
        }
        ShiftReportDto base = frozen != null ? frozen : build(s, "Z");   // shifts closed before Z numbers existed are worked out live
        int copy = s.getZPrints();
        if (print) {
            s.setZPrints(s.getZPrints() + 1);
            sessionRepository.save(s);
        }
        return withCopy(base, print ? copy : Math.max(0, copy - 1));
    }

    /** Called when a shift is closed: gives it the next Z number for its register and keeps the report as it stands. */
    @Transactional
    public void freeze(RegisterSession s) {
        Integer last = sessionRepository.findAll().stream()
                .filter(x -> x.getRegister() != null && s.getRegister() != null && x.getRegister().getId().equals(s.getRegister().getId()))
                .map(RegisterSession::getZNumber).filter(Objects::nonNull).max(Integer::compare).orElse(0);
        s.setZNumber(last + 1);
        ShiftReportDto dto = build(s, "Z");
        try {
            s.setZSnapshot(objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            throw ApiException.badRequest("Could not keep the Z report: " + e.getMessage());
        }
        sessionRepository.save(s);
    }

    private static ShiftReportDto withCopy(ShiftReportDto d, int copy) {
        return new ShiftReportDto(d.type(), d.sessionId(), d.zNumber(), d.registerName(), d.cashierName(), d.openedAt(), d.closedAt(), d.generatedAt(), d.closedBy(), copy,
                d.sales(), d.payments(), d.taxes(), d.refunds(), d.drawer(), d.controls(), d.cashiers(), d.cashMovements());
    }

    private ShiftReportDto build(RegisterSession s, String type) {
        Long id = s.getId();
        Set<String> cashCodes = paymentMethodService.listCashCodes();
        Map<String, String> names = new HashMap<>();
        for (PaymentMethodDto pm : paymentMethodService.listAll()) names.put(pm.code(), pm.name());
        names.put("GIFT_CARD", "Gift card");
        names.put("LOYALTY", "Loyalty points");

        List<SaleOrder> sold = orderRepository.findBySessionIdAndStatusOrderByCreatedAtAsc(id, OrderStatus.COMPLETED);
        BigDecimal items = BigDecimal.ZERO;
        int offline = 0, discounted = 0;
        BigDecimal shelf = BigDecimal.ZERO, afterDiscount = BigDecimal.ZERO, net = BigDecimal.ZERO, tax = BigDecimal.ZERO, total = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO, room = BigDecimal.ZERO;
        Map<String, BigDecimal[]> byTax = new TreeMap<>();   // key code|rate -> {net, tax}
        Map<String, Object[]> pay = new LinkedHashMap<>();   // method -> {count, amount}
        for (SaleOrder o : sold) {
            boolean anyDiscount = false;
            for (OrderLine l : o.getLines()) {
                items = items.add(l.getQuantity());
                BigDecimal gross = l.getUnitPrice().multiply(l.getQuantity());
                BigDecimal lineTotal = OrderCalculator.lineTotal(l);
                shelf = shelf.add(gross);
                afterDiscount = afterDiscount.add(lineTotal);
                if (gross.compareTo(lineTotal) > 0) anyDiscount = true;
                BigDecimal ln = OrderCalculator.lineNetTotal(l), lt = OrderCalculator.lineTax(l);
                String key = (l.getTaxCode() == null ? "" : l.getTaxCode()) + "|" + (l.getTaxRatePercent() == null ? "" : l.getTaxRatePercent().stripTrailingZeros().toPlainString());
                BigDecimal[] t = byTax.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                t[0] = t[0].add(ln);
                t[1] = t[1].add(lt);
            }
            if (anyDiscount) discounted++;
            net = net.add(OrderCalculator.subtotal(o));
            tax = tax.add(OrderCalculator.taxTotal(o));
            total = total.add(OrderCalculator.total(o));
            if (o.getOfflineQueuedAt() != null) offline++;
            if (o.getCreditAmount() != null) credit = credit.add(o.getCreditAmount());
            if (o.getRoomChargeAmount() != null) room = room.add(o.getRoomChargeAmount());
            for (Payment p : o.getPayments()) {
                Object[] a = pay.computeIfAbsent(p.getMethod(), k -> new Object[]{0, BigDecimal.ZERO});
                a[0] = (Integer) a[0] + 1;
                a[1] = ((BigDecimal) a[1]).add(p.getAmount());
            }
        }
        BigDecimal discounts = shelf.subtract(afterDiscount).max(BigDecimal.ZERO);
        Sales sales = new Sales(sold.size(), items, scale(shelf), scale(discounts), scale(net), scale(tax), scale(total),
                sold.isEmpty() ? BigDecimal.ZERO.setScale(2) : total.divide(BigDecimal.valueOf(sold.size()), 2, RoundingMode.HALF_UP));
        List<PaymentLine> payments = new ArrayList<>();
        pay.forEach((m, a) -> payments.add(new PaymentLine(m, names.getOrDefault(m, m), cashCodes.contains(m), (Integer) a[0], scale((BigDecimal) a[1]))));
        List<TaxLine> taxes = new ArrayList<>();
        byTax.forEach((k, v) -> {
            String[] parts = k.split("\\|", -1);
            taxes.add(new TaxLine(parts[0].isEmpty() ? "No tax" : parts[0], parts[1].isEmpty() ? null : new BigDecimal(parts[1]), scale(v[0]), scale(v[1])));
        });

        // returns
        List<SalesReturn> returns = returnsService.returnsForSession(id);
        BigDecimal refundTotal = BigDecimal.ZERO;
        Map<String, Object[]> refundBy = new LinkedHashMap<>();
        for (SalesReturn r : returns) {
            refundTotal = refundTotal.add(r.getRefundTotal());
            Object[] a = refundBy.computeIfAbsent(r.getRefundMethod(), k -> new Object[]{0, BigDecimal.ZERO});
            a[0] = (Integer) a[0] + 1;
            a[1] = ((BigDecimal) a[1]).add(r.getRefundTotal());
        }
        List<PaymentLine> refundLines = new ArrayList<>();
        refundBy.forEach((m, a) -> refundLines.add(new PaymentLine(m, names.getOrDefault(m, m), cashCodes.contains(m), (Integer) a[0], scale((BigDecimal) a[1]))));
        Refunds refunds = new Refunds(returns.size(), scale(refundTotal), refundLines);

        // drawer
        var totals = orderService.computeSessionTotals(id);
        BigDecimal cashRefunds = returnsService.sumCashRefundsForSession(id);
        CashControlService.Totals moved = cashControlService.totals(id);
        BigDecimal expected = s.getStatus() == SessionStatus.CLOSED && s.getExpectedCash() != null ? s.getExpectedCash() : cashControlService.expectedCash(s);
        Drawer drawer = new Drawer(scale(s.getOpeningFloat()), scale(totals.cashIn()), scale(cashRefunds), scale(moved.paidIn()), scale(moved.paidOut()), scale(moved.drops()),
                scale(expected), s.getCountedCash() == null ? null : scale(s.getCountedCash()), s.getCashVariance() == null ? null : scale(s.getCashVariance()), s.getVarianceNote(), s.getCountDetail());

        // controls
        int abandoned = 0;
        BigDecimal abandonedValue = BigDecimal.ZERO;
        for (SaleOrder o : orderRepository.findBySessionIdAndStatusOrderByCreatedAtAsc(id, OrderStatus.VOIDED)) {
            if (o.getLines().isEmpty()) continue;
            abandoned++;
            abandonedValue = abandonedValue.add(OrderCalculator.total(o));
        }
        var cashMovements = cashControlService.movements(id);
        int voidedMoves = (int) cashMovements.stream().filter(m -> m.voided()).count();
        BigDecimal giftSold = giftCardService.soldInRegisterSession(id)[0].add(giftCardService.soldInRegisterSession(id)[1]);
        Controls controls = new Controls(discounted, scale(discounts), abandoned, scale(abandonedValue), offline, scale(credit), scale(room), scale(giftSold), voidedMoves);

        List<CashierLine> cashiers = new ArrayList<>();
        for (CashierSession cs : cashierSessionService.listEntitiesForShift(id)) {
            var t = orderService.computeCashierSessionTotals(cs.getId());
            cashiers.add(new CashierLine(cs.getCashier() == null ? "—" : cs.getCashier().getFullName(), cs.getStatus().name(), cs.getStartedAt(), cs.getEndedAt(), t.saleCount(), scale(t.grossSales())));
        }
        return new ShiftReportDto(type, id, s.getZNumber(), s.getRegister() == null ? "—" : s.getRegister().getName(), s.getCashier() == null ? "—" : s.getCashier().getFullName(),
                s.getOpenedAt(), s.getClosedAt(), Instant.now(), s.getClosedBy(), 0, sales, payments, taxes, refunds, drawer, controls, cashiers, cashMovements);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    private RegisterSession session(Long id) {
        return sessionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Session not found: " + id));
    }
}
