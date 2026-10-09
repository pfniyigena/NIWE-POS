package com.posapp.register.dto;

import com.posapp.order.SessionTotals;
import com.posapp.register.CashierSession;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One cashier's own line in the Z-report's per-cashier breakdown — Task: "The report for
 * register_session (Shift) should show all cashier_session (session report)." A shift can have
 * more than one of these (the "Switch cashier" workflow lets several people ring sales on the same
 * till during the same shift; see {@link com.posapp.register.CashierSession}'s own javadoc), so
 * {@link SessionReportDto} carries a list of these alongside its existing shift-wide totals rather
 * than replacing them — the shift-wide totals stay the reconciliation figures (opening float vs.
 * counted cash is a single, physical drawer, not one per cashier), this is purely "who rang up
 * what, and when they were on the till."
 */
public record CashierSessionReportDto(
        Long cashierSessionId,
        Long cashierId,
        String cashierName,
        Instant startedAt,
        Instant endedAt,
        String status,
        int saleCount,
        BigDecimal grossSales,
        BigDecimal taxCollected,
        BigDecimal cashIn,
        BigDecimal nonCashIn
) {
    public static CashierSessionReportDto from(CashierSession s, SessionTotals totals) {
        var cashier = s.getCashier();
        return new CashierSessionReportDto(
                s.getId(),
                cashier != null ? cashier.getId() : null,
                cashier != null ? cashier.getFullName() : "—",
                s.getStartedAt(),
                s.getEndedAt(),
                s.getStatus().name(),
                totals.saleCount(),
                totals.grossSales(),
                totals.taxCollected(),
                totals.cashIn(),
                totals.nonCashIn()
        );
    }
}
