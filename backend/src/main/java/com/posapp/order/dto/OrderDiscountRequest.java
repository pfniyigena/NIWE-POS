package com.posapp.order.dto;

import java.math.BigDecimal;

/** A discount on the whole sale: a percent or an amount (one of the two), with a reason and, when needed, a manager's PIN. */
public class OrderDiscountRequest {
    private BigDecimal percent;
    private BigDecimal amount;
    private String reason;
    private String approverPin;

    public BigDecimal getPercent() { return percent; }
    public void setPercent(BigDecimal percent) { this.percent = percent; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getApproverPin() { return approverPin; }
    public void setApproverPin(String approverPin) { this.approverPin = approverPin; }
}
