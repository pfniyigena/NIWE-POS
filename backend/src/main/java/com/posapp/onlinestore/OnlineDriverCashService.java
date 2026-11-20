package com.posapp.onlinestore;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.CashSettleItem;
import com.posapp.onlinestore.OnlineStoreDtos.CashSettleRequest;
import com.posapp.onlinestore.OnlineStoreDtos.DriverCashDto;
import com.posapp.onlinestore.OnlineStoreDtos.DriverCashRow;
import com.posapp.onlinestore.OnlineStoreDtos.DriverCashTotal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Driver cash (v1.57.0). A delivery order completed with the cash taken at the door has {@code cashCollected}: money the driver holds until he hands it in.
 * Staff settle each order with what he really handed in; the difference is posted to Cash over / short (short = an expense, over = income) and the order keeps
 * who, when, how much and why. A settlement cannot be undone from here: correct a mistake with a journal entry.
 */
@Service
public class OnlineDriverCashService {

    private final OnlineOrderRepository orders;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;

    public OnlineDriverCashService(OnlineOrderRepository orders, AccountingService accounting, AuditLogService auditLogService) {
        this.orders = orders;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    private static String user() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
    }

    /** driverId: null = everybody, 0 = orders nobody was assigned to, else that driver. */
    @Transactional(readOnly = true)
    public DriverCashDto list(Long driverId) {
        List<DriverCashRow> waiting = new ArrayList<>();
        Map<Long, DriverCashTotal> totals = new LinkedHashMap<>();
        for (OnlineOrder o : orders.findByCashCollectedGreaterThanAndCashSettledAtIsNullOrderByIdAsc(BigDecimal.ZERO)) {
            if (!matches(o, driverId)) continue;
            waiting.add(row(o));
            long key = o.getDriverId() == null ? 0L : o.getDriverId();
            DriverCashTotal t = totals.get(key);
            totals.put(key, new DriverCashTotal(key, o.getDriverName(), t == null ? 1 : t.orders() + 1,
                    (t == null ? BigDecimal.ZERO : t.expected()).add(o.getCashCollected())));
        }
        List<DriverCashRow> settled = orders.findTop200ByCashSettledAtIsNotNullOrderByCashSettledAtDesc().stream()
                .filter(o -> matches(o, driverId)).map(OnlineDriverCashService::row).toList();
        List<DriverCashTotal> sorted = totals.values().stream().sorted(Comparator.comparing(t -> t.driverName() == null ? "~" : t.driverName())).toList();
        return new DriverCashDto(waiting, settled, sorted);
    }

    private static boolean matches(OnlineOrder o, Long driverId) {
        return driverId == null || (driverId == 0 ? o.getDriverId() == null : driverId.equals(o.getDriverId()));
    }

    static Instant deliveredAt(OnlineOrder o) {
        if (o.getReceivedAt() != null) return o.getReceivedAt();
        return o.getEvents().stream().filter(e -> "COMPLETED".equals(e.getStatus())).map(OnlineOrderEvent::getCreatedAt).reduce((a, b) -> b).orElse(o.getUpdatedAt());
    }

    private static DriverCashRow row(OnlineOrder o) {
        return new DriverCashRow(o.getId(), o.getCode(), o.getDriverId(), o.getDriverName(), o.getCustomerName(), deliveredAt(o), o.getCashCollected(),
                o.getCashHandedIn(), o.getCashDifference(), o.getCashSettledAt(), o.getCashSettledBy(), o.getCashNote());
    }

    /**
     * Settles the cash of these orders (all or nothing). The amount handed in may differ from what was collected, but then a reason (3+ characters) is
     * needed; the difference is posted to Cash over / short.
     */
    @Transactional
    public int settle(CashSettleRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) throw ApiException.badRequest(I18n.t("Choose the orders to settle."));
        List<Long> seen = new ArrayList<>();
        for (CashSettleItem item : request.items()) {
            if (seen.contains(item.orderId())) throw ApiException.badRequest(I18n.t("An order is listed twice."));
            seen.add(item.orderId());
            OnlineOrder o = orders.findById(item.orderId()).orElseThrow(() -> ApiException.notFound(I18n.t("Online order not found")));
            if (o.getCashCollected() == null || o.getCashCollected().signum() <= 0) throw ApiException.badRequest(I18n.t("Order {0} has no cash to hand in.", o.getCode()));
            if (o.getCashSettledAt() != null) throw ApiException.badRequest(I18n.t("The cash of order {0} was already settled.", o.getCode()));
            BigDecimal amount = item.amount() == null ? null : item.amount().setScale(2, java.math.RoundingMode.HALF_UP);
            if (amount == null || amount.signum() < 0) throw ApiException.badRequest(I18n.t("Enter the amount handed in for order {0}.", o.getCode()));
            BigDecimal diff = amount.subtract(o.getCashCollected());
            String note = item.note() == null ? null : item.note().trim();
            if (diff.signum() != 0 && (note == null || note.length() < 3)) {
                throw ApiException.badRequest(I18n.t("Order {0}: the amount differs from the cash collected ({1}). Write the reason.", o.getCode(), o.getCashCollected().toPlainString()));
            }
            o.setCashHandedIn(amount);
            o.setCashDifference(diff);
            o.setCashNote(note == null || note.isEmpty() ? null : note);
            o.setCashSettledAt(Instant.now());
            o.setCashSettledBy(user());
            OnlineOrderEvent ev = new OnlineOrderEvent();
            ev.setOnlineOrder(o);
            ev.setStatus(o.getStatus());
            ev.setNote(diff.signum() == 0 ? I18n.t("Driver cash handed in: {0}", amount.toPlainString())
                    : I18n.t("Driver cash handed in: {0} (difference {1})", amount.toPlainString(), diff.toPlainString()));
            ev.setByUser(user());
            o.getEvents().add(ev);
            orders.save(o);
            if (diff.signum() != 0) {
                accounting.postDriverCashVariance(o.getId(), diff, I18n.t("Driver cash, order {0}{1}: collected {2}, handed in {3}", o.getCode(),
                        o.getDriverName() == null ? "" : " (" + o.getDriverName() + ")", o.getCashCollected().toPlainString(), amount.toPlainString())
                        + (note == null ? "" : " — " + note), null);
            }
            auditLogService.record("ONLINE_DRIVER_CASH", "Driver cash for online order " + o.getCode() + ": collected " + o.getCashCollected().toPlainString()
                    + ", handed in " + amount.toPlainString() + (diff.signum() == 0 ? "" : ", difference " + diff.toPlainString()), "OnlineOrder", o.getId());
        }
        return request.items().size();
    }
}
