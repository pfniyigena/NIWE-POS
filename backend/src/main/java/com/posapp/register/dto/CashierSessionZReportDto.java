package com.posapp.register.dto;

import com.posapp.order.SessionTotals;
import com.posapp.register.CashierSession;
import com.posapp.register.RegisterSession;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A standalone, printable report for one cashier's own working session — deliberately its own
 * screen/DTO, not the same thing as {@link SessionReportDto} (the whole shift's Z-report). Task:
 * "We need to differentiate CashierSession Z Report with RegisterSession Z Report" — before this,
 * the plain "Sessions" screen's per-cashier-session "Report" link pointed at the *shift*'s Z-report
 * ({@code /sessions/:id/report}), so a cashier looking up their own ended session saw the whole
 * till's totals (every cashier who worked it, plus the physical cash-drawer reconciliation) rather
 * than just their own slice of it. This DTO backs a separate report ({@code GET
 * /api/cashier-sessions/:id/report}) scoped to exactly one {@link CashierSession}: its own
 * start/end time and its own sales figures, computed the same way {@link CashierSessionReportDto}
 * already is for the shift report's per-cashier breakdown rows — but here it's the whole report,
 * with the register/shift context a standalone page needs (registerName, the shift it belongs to)
 * that an embedded row can leave to its parent.
 *
 * <p>Deliberately excludes cash-drawer reconciliation (opening float, expected/counted cash,
 * variance) and refunds — both are shift-wide, physical-till concepts (one drawer, not one per
 * cashier), and {@code SalesReturn} has no cashier-session reference to attribute a refund to one
 * cashier's stint even if it wanted to (see {@code ReturnsService.sumCashRefundsForSession}'s own
 * javadoc). Those stay exclusively on the RegisterSession-level Z-report.</p>
 */
public record CashierSessionZReportDto(
        Long cashierSessionId,
        Long registerSessionId,
        Long registerId,
        String registerName,
        String shiftStatus,
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
    public static CashierSessionZReportDto from(CashierSession s, SessionTotals totals) {
        RegisterSession registerSession = s.getRegisterSession();
        var register = registerSession != null ? registerSession.getRegister() : null;
        var cashier = s.getCashier();
        return new CashierSessionZReportDto(
                s.getId(),
                registerSession != null ? registerSession.getId() : null,
                register != null ? register.getId() : null,
                register != null ? register.getName() : "—",
                registerSession != null ? registerSession.getStatus().name() : null,
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
