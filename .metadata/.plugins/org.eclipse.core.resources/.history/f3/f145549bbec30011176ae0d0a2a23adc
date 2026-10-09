package com.posapp.credit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Fourier: "Let record payment per invoice not per customer (all outstanding invoices)" — the
 * body for recording a payment against exactly one invoice (see {@code
 * InvoiceController#recordPayment}/{@code CustomerCreditService#recordInvoicePayment}). Unlike
 * {@link RecordPaymentRequest} there is no {@code allocations} list and no {@code orderId}/
 * {@code orderType} to pick — the invoice is the one named in the URL, and the whole amount
 * always applies to it; the admin can never spread one payment across several of a customer's
 * outstanding invoices from this screen.
 */
public class RecordInvoicePaymentRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    /** Same active-code validation a checkout/refund tender goes through — see {@code RecordPaymentRequest.method}. */
    @NotBlank(message = "Payment method is required")
    private String method;

    private String note;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
