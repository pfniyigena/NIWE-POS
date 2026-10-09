package com.posapp.salessettings.dto;

import java.util.List;

/** Fourier: "Add invoices receiver emails in sales settings" — the admin form submits the list form directly; SalesSettingsService does the CSV parsing/joining. */
public class SalesSettingsRequest {

    private List<String> invoiceReceiverEmails;

    public List<String> getInvoiceReceiverEmails() { return invoiceReceiverEmails; }
    public void setInvoiceReceiverEmails(List<String> invoiceReceiverEmails) { this.invoiceReceiverEmails = invoiceReceiverEmails; }
}
