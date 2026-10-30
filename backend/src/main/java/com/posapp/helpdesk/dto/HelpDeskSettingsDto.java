package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskSettings;

import java.util.List;

public class HelpDeskSettingsDto {
    private Long id;
    private List<String> notificationEmails;
    private boolean publicEnabled;
    private boolean slaEnabled;
    private boolean slaInternal;
    private int slaResponseLow;
    private int slaResponseMedium;
    private int slaResponseHigh;
    private int slaResponseUrgent;
    private int slaResolutionLow;
    private int slaResolutionMedium;
    private int slaResolutionHigh;
    private int slaResolutionUrgent;
    private boolean escalationEnabled;
    private int warnHours;
    private boolean replyEmailEnabled;
    private boolean publicReplyEnabled;
    private boolean satisfactionEnabled;
    private int autoCloseDays;

    public static HelpDeskSettingsDto from(HelpDeskSettings e, List<String> notificationEmails) {
        HelpDeskSettingsDto d = new HelpDeskSettingsDto();
        d.id = e.getId();
        d.notificationEmails = notificationEmails;
        d.publicEnabled = e.isPublicEnabled();
        d.slaEnabled = e.isSlaEnabled();
        d.slaInternal = e.isSlaInternal();
        d.slaResponseLow = e.getSlaResponseLow();
        d.slaResponseMedium = e.getSlaResponseMedium();
        d.slaResponseHigh = e.getSlaResponseHigh();
        d.slaResponseUrgent = e.getSlaResponseUrgent();
        d.slaResolutionLow = e.getSlaResolutionLow();
        d.slaResolutionMedium = e.getSlaResolutionMedium();
        d.slaResolutionHigh = e.getSlaResolutionHigh();
        d.slaResolutionUrgent = e.getSlaResolutionUrgent();
        d.escalationEnabled = e.isEscalationEnabled();
        d.warnHours = e.getWarnHours();
        d.replyEmailEnabled = e.isReplyEmailEnabled();
        d.publicReplyEnabled = e.isPublicReplyEnabled();
        d.satisfactionEnabled = e.isSatisfactionEnabled();
        d.autoCloseDays = e.getAutoCloseDays();
        return d;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public List<String> getNotificationEmails() { return notificationEmails; }
    public void setNotificationEmails(List<String> notificationEmails) { this.notificationEmails = notificationEmails; }
    public boolean isPublicEnabled() { return publicEnabled; }
    public void setPublicEnabled(boolean publicEnabled) { this.publicEnabled = publicEnabled; }
    public boolean isSlaEnabled() { return slaEnabled; }
    public void setSlaEnabled(boolean slaEnabled) { this.slaEnabled = slaEnabled; }
    public boolean isSlaInternal() { return slaInternal; }
    public void setSlaInternal(boolean slaInternal) { this.slaInternal = slaInternal; }
    public int getSlaResponseLow() { return slaResponseLow; }
    public void setSlaResponseLow(int v) { this.slaResponseLow = v; }
    public int getSlaResponseMedium() { return slaResponseMedium; }
    public void setSlaResponseMedium(int v) { this.slaResponseMedium = v; }
    public int getSlaResponseHigh() { return slaResponseHigh; }
    public void setSlaResponseHigh(int v) { this.slaResponseHigh = v; }
    public int getSlaResponseUrgent() { return slaResponseUrgent; }
    public void setSlaResponseUrgent(int v) { this.slaResponseUrgent = v; }
    public int getSlaResolutionLow() { return slaResolutionLow; }
    public void setSlaResolutionLow(int v) { this.slaResolutionLow = v; }
    public int getSlaResolutionMedium() { return slaResolutionMedium; }
    public void setSlaResolutionMedium(int v) { this.slaResolutionMedium = v; }
    public int getSlaResolutionHigh() { return slaResolutionHigh; }
    public void setSlaResolutionHigh(int v) { this.slaResolutionHigh = v; }
    public int getSlaResolutionUrgent() { return slaResolutionUrgent; }
    public void setSlaResolutionUrgent(int v) { this.slaResolutionUrgent = v; }
    public boolean isEscalationEnabled() { return escalationEnabled; }
    public void setEscalationEnabled(boolean escalationEnabled) { this.escalationEnabled = escalationEnabled; }
    public int getWarnHours() { return warnHours; }
    public void setWarnHours(int warnHours) { this.warnHours = warnHours; }
    public boolean isReplyEmailEnabled() { return replyEmailEnabled; }
    public void setReplyEmailEnabled(boolean replyEmailEnabled) { this.replyEmailEnabled = replyEmailEnabled; }
    public boolean isPublicReplyEnabled() { return publicReplyEnabled; }
    public void setPublicReplyEnabled(boolean publicReplyEnabled) { this.publicReplyEnabled = publicReplyEnabled; }
    public boolean isSatisfactionEnabled() { return satisfactionEnabled; }
    public void setSatisfactionEnabled(boolean satisfactionEnabled) { this.satisfactionEnabled = satisfactionEnabled; }
    public int getAutoCloseDays() { return autoCloseDays; }
    public void setAutoCloseDays(int autoCloseDays) { this.autoCloseDays = autoCloseDays; }
}
