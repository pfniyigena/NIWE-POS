package com.posapp.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public class RestaurantCheckoutRequest {
    @NotEmpty(message = "At least one payment is required")
    @Valid
    private List<PaymentEntry> payments;

    /** Hotel module: the in-house guest's booking, required when any payment entry uses the "Charge to room" method (already known for a room-service order). */
    private Long roomBookingId;

    public Long getRoomBookingId() { return roomBookingId; }
    public void setRoomBookingId(Long roomBookingId) { this.roomBookingId = roomBookingId; }
    public List<PaymentEntry> getPayments() { return payments; }
    public void setPayments(List<PaymentEntry> payments) { this.payments = payments; }

    /** One tender toward the bill (Fourier: "split & merge bills") — several entries here is what lets one table's bill be paid with more than one method or by more than one guest. */
    public static class PaymentEntry {
        @NotNull(message = "Payment method is required")
        private String method;

        @NotNull(message = "Payment amount is required")
        @Positive(message = "Payment amount must be greater than zero")
        private BigDecimal amount;

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }
}
