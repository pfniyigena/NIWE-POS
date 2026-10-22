package com.posapp.restaurant.dto;

import java.math.BigDecimal;

/** Sets (or, with zero or nothing, removes) the order discount: a percentage or a fixed amount, with the reason. */
public class OrderDiscountRequest {
    private BigDecimal percent;
    private BigDecimal amount;
    private String reason;

    public BigDecimal getPercent() { return percent; }
    public void setPercent(BigDecimal percent) { this.percent = percent; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
