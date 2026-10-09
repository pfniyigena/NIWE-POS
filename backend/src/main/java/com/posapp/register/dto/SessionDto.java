package com.posapp.register.dto;

import com.posapp.order.SessionTotals;
import com.posapp.register.RegisterSession;

import java.math.BigDecimal;
import java.time.Instant;

public record SessionDto(
        Long id,
        Long registerId,
        String registerName,
        Long cashierId,
        String cashierName,
        BigDecimal openingFloat,
        Instant openedAt,
        Instant closedAt,
        BigDecimal countedCash,
        BigDecimal cashVariance,
        String status,
        int saleCount,
        BigDecimal grossSales
) {
    /** Used by the "Shifts" admin screen and the plain Sessions list — sales totals for this shift, computed by the caller (OrderService.computeSessionTotals) so this DTO doesn't need to depend on the order package's service. */
    public static SessionDto from(RegisterSession s, SessionTotals totals) {
        var register = s.getRegister();
        var cashier = s.getCashier();
        return new SessionDto(
                s.getId(),
                register != null ? register.getId() : null,
                register != null ? register.getName() : "—",
                cashier != null ? cashier.getId() : null,
                cashier != null ? cashier.getFullName() : "—",
                s.getOpeningFloat(),
                s.getOpenedAt(),
                s.getClosedAt(),
                s.getCountedCash(),
                s.getCashVariance(),
                s.getStatus().name(),
                totals.saleCount(),
                totals.grossSales()
        );
    }
}
