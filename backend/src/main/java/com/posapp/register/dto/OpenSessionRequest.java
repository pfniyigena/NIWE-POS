package com.posapp.register.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class OpenSessionRequest {
    @NotNull(message = "Opening float is required")
    @PositiveOrZero(message = "Opening float cannot be negative")
    private BigDecimal openingFloat;

    /**
     * Which staff member this shift's initial cashier session is assigned to. Opening a shift is
     * ADMIN-only (Change 3), but the admin usually isn't the one who'll actually ring sales on
     * it, so the admin picks who is — see RegisterService.openSession.
     */
    @NotNull(message = "Select which staff member this session is assigned to")
    private Long cashierId;

    public BigDecimal getOpeningFloat() { return openingFloat; }
    public void setOpeningFloat(BigDecimal openingFloat) { this.openingFloat = openingFloat; }
    public Long getCashierId() { return cashierId; }
    public void setCashierId(Long cashierId) { this.cashierId = cashierId; }
}
