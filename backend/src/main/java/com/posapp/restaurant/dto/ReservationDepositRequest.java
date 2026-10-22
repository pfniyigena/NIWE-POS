package com.posapp.restaurant.dto;

public class ReservationDepositRequest {
    private String method;
    /** Optional: change the amount asked for before taking it. */
    private java.math.BigDecimal amount;

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public java.math.BigDecimal getAmount() { return amount; }
    public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
}
