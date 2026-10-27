package com.posapp.salessettings.dto;

import java.util.List;

/** Fourier: "Add invoices receiver emails in sales settings" — the admin form submits the list form directly; SalesSettingsService does the CSV parsing/joining. */
public class SalesSettingsRequest {

    private List<String> invoiceReceiverEmails;
    /** Null means "leave as it is", so older clients that only send the emails keep working. */
    private Boolean promotionsEnabled;
    private Boolean giftCardsEnabled;
    private Boolean loyaltyEnabled;

    public Boolean getPromotionsEnabled() { return promotionsEnabled; }
    public void setPromotionsEnabled(Boolean v) { this.promotionsEnabled = v; }
    public Boolean getGiftCardsEnabled() { return giftCardsEnabled; }
    public void setGiftCardsEnabled(Boolean v) { this.giftCardsEnabled = v; }
    public Boolean getLoyaltyEnabled() { return loyaltyEnabled; }
    public void setLoyaltyEnabled(Boolean v) { this.loyaltyEnabled = v; }

    public List<String> getInvoiceReceiverEmails() { return invoiceReceiverEmails; }
    public void setInvoiceReceiverEmails(List<String> invoiceReceiverEmails) { this.invoiceReceiverEmails = invoiceReceiverEmails; }
}
