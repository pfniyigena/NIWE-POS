package com.posapp.register.dto;

import jakarta.validation.constraints.NotBlank;

/** A cashier identifying themselves at the terminal by PIN alone — see CashierSessionService.joinShift. */
public class PinRequest {

    @NotBlank(message = "PIN is required")
    private String pin;

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
}
