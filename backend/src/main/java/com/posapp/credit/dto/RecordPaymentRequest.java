package com.posapp.credit.dto;

import com.posapp.credit.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * Recording a customer payment (Task: "Customer payment later" / "Partial settlement"). {@code
 * allocations} must sum to exactly {@code amount} — see {@code CustomerCreditService
 * .recordPayment}'s validation and its class javadoc for why there's no floating/unapplied
 * balance concept in this design.
 */
public class RecordPaymentRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    /** Task: "To record Payment in Sale Order add payment mode (CASH, CHEQUE, ETC)" — validated as an active PaymentMethodEntity code, same as a checkout/refund tender. */
    @NotBlank(message = "Payment method is required")
    private String method;

    private String note;

    @NotEmpty(message = "At least one order allocation is required")
    @Valid
    private List<AllocationEntry> allocations;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<AllocationEntry> getAllocations() { return allocations; }
    public void setAllocations(List<AllocationEntry> allocations) { this.allocations = allocations; }

    public static class AllocationEntry {
        @NotNull(message = "Order id is required")
        private Long orderId;

        /** Which kind of order {@link #orderId} refers to — see CustomerPaymentAllocation's "exclusive arc" javadoc. Defaults to POS_SALE when omitted, so existing callers/tests written before Sales Orders existed keep working unchanged. */
        private OrderType orderType = OrderType.POS_SALE;

        @NotNull(message = "Allocation amount is required")
        @Positive(message = "Allocation amount must be greater than zero")
        private BigDecimal amount;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public OrderType getOrderType() { return orderType; }
        public void setOrderType(OrderType orderType) { this.orderType = orderType; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }
}
