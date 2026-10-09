package com.posapp.register.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class CloseSessionRequest {
    @NotNull(message = "Counted cash is required")
    @PositiveOrZero(message = "Counted cash cannot be negative")
    private BigDecimal countedCash;

    public BigDecimal getCountedCash() { return countedCash; }
    public void setCountedCash(BigDecimal countedCash) { this.countedCash = countedCash; }
}
