package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

/** Advances a DELIVERY order's hand-off status — same shape as KitchenStatusRequest. */
public class DeliveryStatusRequest {
    @NotBlank(message = "Status is required")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
