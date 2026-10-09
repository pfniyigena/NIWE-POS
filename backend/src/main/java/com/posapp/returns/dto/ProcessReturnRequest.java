package com.posapp.returns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class ProcessReturnRequest {

    @NotNull(message = "Order is required")
    private Long orderId;

    @NotEmpty(message = "At least one return line is required")
    @Valid
    private List<ReturnLineEntry> lines;

    @NotNull(message = "Refund method is required")
    private String refundMethod;

    private String reason;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public List<ReturnLineEntry> getLines() { return lines; }
    public void setLines(List<ReturnLineEntry> lines) { this.lines = lines; }
    public String getRefundMethod() { return refundMethod; }
    public void setRefundMethod(String refundMethod) { this.refundMethod = refundMethod; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public static class ReturnLineEntry {
        @NotNull(message = "Order line is required")
        private Long orderLineId;

        @Positive(message = "Return quantity must be greater than zero")
        private int quantity;

        public Long getOrderLineId() { return orderLineId; }
        public void setOrderLineId(Long orderLineId) { this.orderLineId = orderLineId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
