package com.posapp.returns.dto;

import jakarta.validation.constraints.NotNull;

/** Voiding a completed sale: it is refunded in full, kept on record, and needs a reason (and a manager's approval, as the POS settings say). */
public class VoidSaleRequest {
    @NotNull(message = "Order is required")
    private Long orderId;
    private String reason;
    /** How the money goes back; left out = the way the customer mostly paid. */
    private String refundMethod;
    private String approverPin;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRefundMethod() { return refundMethod; }
    public void setRefundMethod(String refundMethod) { this.refundMethod = refundMethod; }
    public String getApproverPin() { return approverPin; }
    public void setApproverPin(String approverPin) { this.approverPin = approverPin; }
}
