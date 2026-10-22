package com.posapp.restaurant.dto;

public class ReservationCancelRequest {
    private String reason;
    /** REFUND or FORFEIT when a deposit is held; blank follows the restaurant's refund window. */
    private String deposit;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDeposit() { return deposit; }
    public void setDeposit(String deposit) { this.deposit = deposit; }
}
