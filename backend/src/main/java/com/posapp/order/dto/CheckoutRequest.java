package com.posapp.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CheckoutRequest {

    @NotEmpty(message = "At least one payment is required")
    @Valid
    private List<PaymentEntry> payments;

    /**
     * Optional explicit due date for this sale's credit portion (Task: "Optional due date/payment
     * terms"). Only meaningful when at least one payment entry uses a credit-flagged method; when
     * omitted, OrderService.checkout falls back to Customer.defaultCreditTermDays, and if that's
     * also unset, the order simply has no due date.
     */
    private LocalDate dueDate;

    /** Hotel module: the in-house guest's booking, required when any payment entry uses the "Charge to room" method. */
    private Long roomBookingId;

    public Long getRoomBookingId() { return roomBookingId; }
    public void setRoomBookingId(Long roomBookingId) { this.roomBookingId = roomBookingId; }
    public List<PaymentEntry> getPayments() { return payments; }
    public void setPayments(List<PaymentEntry> payments) { this.payments = payments; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

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
