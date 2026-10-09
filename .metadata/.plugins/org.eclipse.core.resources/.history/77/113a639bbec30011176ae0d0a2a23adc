package com.posapp.salessettings.dto;

import com.posapp.salessettings.SalesSettings;

import java.util.List;

/** Admin > Sales > Settings — see SalesSettingsService's own javadoc for the CSV-storage/List-exposure split this DTO is the "List" side of. */
public class SalesSettingsDto {

    private Long id;
    private List<String> invoiceReceiverEmails;

    public static SalesSettingsDto from(SalesSettings entity, List<String> invoiceReceiverEmails) {
        SalesSettingsDto dto = new SalesSettingsDto();
        dto.id = entity.getId();
        dto.invoiceReceiverEmails = invoiceReceiverEmails;
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public List<String> getInvoiceReceiverEmails() { return invoiceReceiverEmails; }
    public void setInvoiceReceiverEmails(List<String> invoiceReceiverEmails) { this.invoiceReceiverEmails = invoiceReceiverEmails; }
}
