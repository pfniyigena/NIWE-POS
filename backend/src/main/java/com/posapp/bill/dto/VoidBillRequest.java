package com.posapp.bill.dto;

/** Body for POST /api/bills/{id}/void — see BillService.voidBill. */
public class VoidBillRequest {

    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
