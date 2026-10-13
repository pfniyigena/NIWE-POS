package com.posapp.paymentmethod.dto;

import jakarta.validation.constraints.NotBlank;

/** Create request — code is set once and never editable afterward (see UpdatePaymentMethodRequest). */
public class PaymentMethodRequest {

    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    private boolean cash = false;

    private boolean credit = false;
    private boolean roomCharge = false;

    /** Task: "Make payment method order dynamic" — lower shows first at checkout; ties break by name. */
    private int sortOrder = 0;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isCash() { return cash; }
    public void setCash(boolean cash) { this.cash = cash; }
    public boolean isCredit() { return credit; }
    public void setCredit(boolean credit) { this.credit = credit; }
    public boolean isRoomCharge() { return roomCharge; }
    public void setRoomCharge(boolean roomCharge) { this.roomCharge = roomCharge; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
