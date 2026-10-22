package com.posapp.restaurant.dto;

/** Waives or restores the service charge on one order. */
public class ServiceChargeRequest {
    private boolean waived;

    public boolean isWaived() { return waived; }
    public void setWaived(boolean waived) { this.waived = waived; }
}
