package com.posapp.salessettings.dto;

import com.posapp.salessettings.SalesSettings;

import java.util.List;

/** Admin > Sales > Settings — see SalesSettingsService's own javadoc for the CSV-storage/List-exposure split this DTO is the "List" side of. */
public class SalesSettingsDto {

    private Long id;
    private List<String> invoiceReceiverEmails;
    private boolean promotionsEnabled;
    private boolean giftCardsEnabled;
    private boolean loyaltyEnabled;

    public static SalesSettingsDto from(SalesSettings entity, List<String> invoiceReceiverEmails, boolean loyaltyEnabled) {
        SalesSettingsDto dto = new SalesSettingsDto();
        dto.promotionsEnabled = entity.isPromotionsEnabled();
        dto.giftCardsEnabled = entity.isGiftCardsEnabled();
        dto.loyaltyEnabled = loyaltyEnabled;
        dto.id = entity.getId();
        dto.invoiceReceiverEmails = invoiceReceiverEmails;
        return dto;
    }

    public boolean isPromotionsEnabled() { return promotionsEnabled; }
    public boolean isGiftCardsEnabled() { return giftCardsEnabled; }
    public boolean isLoyaltyEnabled() { return loyaltyEnabled; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public List<String> getInvoiceReceiverEmails() { return invoiceReceiverEmails; }
    public void setInvoiceReceiverEmails(List<String> invoiceReceiverEmails) { this.invoiceReceiverEmails = invoiceReceiverEmails; }
}
