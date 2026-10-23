package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskSettings;

import java.util.List;

/** Admin > Help Desk > Settings — see HelpDeskSettingsService's own javadoc for the CSV-storage/List-exposure split this DTO is the "List" side of. */
public class HelpDeskSettingsDto {

    private Long id;
    private List<String> notificationEmails;
    private boolean publicEnabled;

    public static HelpDeskSettingsDto from(HelpDeskSettings entity, List<String> notificationEmails) {
        HelpDeskSettingsDto dto = new HelpDeskSettingsDto();
        dto.id = entity.getId();
        dto.notificationEmails = notificationEmails;
        dto.publicEnabled = entity.isPublicEnabled();
        return dto;
    }

    public boolean isPublicEnabled() { return publicEnabled; }
    public void setPublicEnabled(boolean publicEnabled) { this.publicEnabled = publicEnabled; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public List<String> getNotificationEmails() { return notificationEmails; }
    public void setNotificationEmails(List<String> notificationEmails) { this.notificationEmails = notificationEmails; }
}
