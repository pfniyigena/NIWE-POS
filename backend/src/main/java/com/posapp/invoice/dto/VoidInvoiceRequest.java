package com.posapp.invoice.dto;

/** Body for POST /api/invoices/{id}/void — see InvoiceService.voidInvoice. */
public class VoidInvoiceRequest {

    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
