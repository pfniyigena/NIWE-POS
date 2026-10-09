package com.posapp.invoice.dto;

import com.posapp.invoice.PaymentDisposition;
import jakarta.validation.constraints.NotNull;

/**
 * Body for POST /api/invoices/{id}/cancel-paid — see {@code InvoiceService.cancelPaidInvoice}.
 * {@code refundMethod} is only required when {@code disposition} is {@code REFUND} (validated in
 * the service, not here, since whether it's required depends on another field).
 */
public class CancelPaidInvoiceRequest {

    @NotNull(message = "Choose how to handle the payment already recorded on this invoice")
    private PaymentDisposition disposition;

    private String refundMethod;

    private String reason;

    public PaymentDisposition getDisposition() { return disposition; }
    public void setDisposition(PaymentDisposition disposition) { this.disposition = disposition; }
    public String getRefundMethod() { return refundMethod; }
    public void setRefundMethod(String refundMethod) { this.refundMethod = refundMethod; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
