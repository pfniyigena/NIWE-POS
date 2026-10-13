package com.posapp.paymentmethod.dto;

import jakarta.validation.constraints.NotBlank;

/** Update request — deliberately excludes code: it's the stable identifier stored on historical Payment/SalesReturn rows. */
public class UpdatePaymentMethodRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private boolean cash = false;
    private boolean credit = false;
    private boolean roomCharge = false;
    private boolean active = true;

    /** Task: "Make payment method order dynamic" — lower shows first at checkout; ties break by name. */
    private int sortOrder = 0;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isCash() { return cash; }
    public void setCash(boolean cash) { this.cash = cash; }
    public boolean isCredit() { return credit; }
    public void setCredit(boolean credit) { this.credit = credit; }
    public boolean isRoomCharge() { return roomCharge; }
    public void setRoomCharge(boolean roomCharge) { this.roomCharge = roomCharge; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
