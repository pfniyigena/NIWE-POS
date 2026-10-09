package com.posapp.register.dto;

import com.posapp.register.CashierSession;

import java.time.Instant;

public record CashierSessionDto(
        Long id,
        Long registerSessionId,
        String registerName,
        String shiftStatus,
        Long cashierId,
        String cashierName,
        Instant startedAt,
        Instant endedAt,
        String status,
        /** Task: "Add customer-facing double screen" — see CashierSession.activeOrderId's javadoc. */
        Long activeOrderId
) {
    public static CashierSessionDto from(CashierSession s) {
        var registerSession = s.getRegisterSession();
        var register = registerSession != null ? registerSession.getRegister() : null;
        var cashier = s.getCashier();
        return new CashierSessionDto(
                s.getId(),
                registerSession != null ? registerSession.getId() : null,
                register != null ? register.getName() : "—",
                registerSession != null ? registerSession.getStatus().name() : null,
                cashier != null ? cashier.getId() : null,
                cashier != null ? cashier.getFullName() : "—",
                s.getStartedAt(),
                s.getEndedAt(),
                s.getStatus().name(),
                s.getActiveOrderId()
        );
    }
}
