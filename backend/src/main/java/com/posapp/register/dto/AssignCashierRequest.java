package com.posapp.register.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Admin adds another staff member's cashier session directly onto an already-open shift — no PIN
 * needed, since the admin is already authorized. This is how an admin fixes "I opened this shift
 * for myself/one cashier, now I need a session for a different cashier on the same shift" without
 * having to know that cashier's PIN or stand at the physical terminal — see
 * CashierSessionService.assignCashier.
 */
public class AssignCashierRequest {

    @NotNull(message = "Select which staff member to add to this shift")
    private Long cashierId;

    public Long getCashierId() { return cashierId; }
    public void setCashierId(Long cashierId) { this.cashierId = cashierId; }
}
