package com.posapp.restaurant.dto;

public class SoldOutRequest {
    private boolean soldOut;
    private String reason;

    public boolean isSoldOut() { return soldOut; }
    public void setSoldOut(boolean soldOut) { this.soldOut = soldOut; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
