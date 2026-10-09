package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskSettings;

import java.util.List;

/** Admin > Help Desk > Settings — see HelpDeskSettingsService's own javadoc for the CSV-storage/List-exposure split this DTO is the "List" side of. */
public class HelpDeskSettingsDto {

    private Long id;
    private List<String> notificationEmails;

    public static HelpDeskSettingsDto from(HelpDeskSettings entity, List<String> notificationEmails) {
        HelpDeskSettingsDto dto = new HelpDeskSettingsDto();
        dto.id = entity.getId();
        dto.notificationEmails = notificationEmails;
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public List<String> getNotificationEmails() { return notificationEmails; }
    public void setNotificationEmails(List<String> notificationEmails) { this.notificationEmails = notificationEmails; }
}
