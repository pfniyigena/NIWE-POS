package com.posapp.email;

/**
 * Shared request body for every "email this document to the customer" endpoint (Fourier:
 * "sending other documents like quotations, invoices, receipts, etc to the customer's email") —
 * QuotationController, InvoiceController, OrderController's own {@code /email}/{@code
 * /email-receipt} actions. {@code email} is optional: null/blank means "use whatever's on file
 * for this customer," a real address overrides it for this one send only (never saved back onto
 * the customer).
 */
public class EmailDocumentRequest {

    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
