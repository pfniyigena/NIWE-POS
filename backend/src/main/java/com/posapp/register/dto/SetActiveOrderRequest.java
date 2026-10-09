package com.posapp.register.dto;

/**
 * Sets (or clears, with a null {@code orderId}) which tab is "on screen" for a customer-facing
 * display to poll — see CashierSession.activeOrderId's javadoc. No {@code @NotNull}: unlike
 * PinRequest, clearing the pointer is a legitimate, expected call (e.g. between the last tab being
 * discarded and a new one being created), not a validation error.
 */
public class SetActiveOrderRequest {

    private Long orderId;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
