package com.posapp.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Fourier: "The Purchase order should flow same workflow as Sale order" — the body for recording
 * a payment against exactly one {@code com.posapp.bill.Bill} (see {@code
 * BillController#recordPayment}/{@code SupplierPaymentService#recordBillPayment}), the
 * accounts-payable mirror of {@code com.posapp.credit.dto.RecordInvoicePaymentRequest}. No
 * allocations list and no purchase-order id to pick — the bill is the one named in the URL, and
 * the whole amount always applies to it.
 *
 * <p>Fourier: "Add payment method when recording bill payment" — {@code method} mirrors {@code
 * RecordInvoicePaymentRequest.method} exactly: required, validated as an active {@code
 * PaymentMethodEntity} code. Deliberately not added to {@code RecordSupplierPaymentRequest} (the
 * general supplier-wide allocation screen) — see {@code SupplierPayment.method}'s own javadoc.
 */
public class RecordBillPaymentRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

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
