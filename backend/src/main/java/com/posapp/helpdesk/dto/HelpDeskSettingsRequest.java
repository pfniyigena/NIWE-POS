package com.posapp.helpdesk.dto;

import java.util.List;

/** Every field is optional: what is left out is not changed. */
public class HelpDeskSettingsRequest {
    private List<String> notificationEmails;
    private Boolean publicEnabled;
    private Boolean slaEnabled;
    private Boolean slaInternal;
    private Integer slaResponseLow;
    private Integer slaResponseMedium;
    private Integer slaResponseHigh;
    private Integer slaResponseUrgent;
    private Integer slaResolutionLow;
    private Integer slaResolutionMedium;
    private Integer slaResolutionHigh;
    private Integer slaResolutionUrgent;
    private Boolean escalationEnabled;
    private Integer warnHours;
    private Boolean replyEmailEnabled;
    private Boolean publicReplyEnabled;
    private Boolean satisfactionEnabled;
    private Integer autoCloseDays;

    public List<String> getNotificationEmails() { return notificationEmails; }
    public void setNotificationEmails(List<String> notificationEmails) { this.notificationEmails = notificationEmails; }
    public Boolean getPublicEnabled() { return publicEnabled; }
    public void setPublicEnabled(Boolean publicEnabled) { this.publicEnabled = publicEnabled; }
    public Boolean getSlaEnabled() { return slaEnabled; }
    public void setSlaEnabled(Boolean slaEnabled) { this.slaEnabled = slaEnabled; }
    public Boolean getSlaInternal() { return slaInternal; }
    public void setSlaInternal(Boolean slaInternal) { this.slaInternal = slaInternal; }
    public Integer getSlaResponseLow() { return slaResponseLow; }
    public void setSlaResponseLow(Integer v) { this.slaResponseLow = v; }
    public Integer getSlaResponseMedium() { return slaResponseMedium; }
    public void setSlaResponseMedium(Integer v) { this.slaResponseMedium = v; }
    public Integer getSlaResponseHigh() { return slaResponseHigh; }
    public void setSlaResponseHigh(Integer v) { this.slaResponseHigh = v; }
    public Integer getSlaResponseUrgent() { return slaResponseUrgent; }
    public void setSlaResponseUrgent(Integer v) { this.slaResponseUrgent = v; }
    public Integer getSlaResolutionLow() { return slaResolutionLow; }
    public void setSlaResolutionLow(Integer v) { this.slaResolutionLow = v; }
    public Integer getSlaResolutionMedium() { return slaResolutionMedium; }
    public void setSlaResolutionMedium(Integer v) { this.slaResolutionMedium = v; }
    public Integer getSlaResolutionHigh() { return slaResolutionHigh; }
    public void setSlaResolutionHigh(Integer v) { this.slaResolutionHigh = v; }
    public Integer getSlaResolutionUrgent() { return slaResolutionUrgent; }
    public void setSlaResolutionUrgent(Integer v) { this.slaResolutionUrgent = v; }
    public Boolean getEscalationEnabled() { return escalationEnabled; }
    public void setEscalationEnabled(Boolean escalationEnabled) { this.escalationEnabled = escalationEnabled; }
    public Integer getWarnHours() { return warnHours; }
    public void setWarnHours(Integer warnHours) { this.warnHours = warnHours; }
    public Boolean getReplyEmailEnabled() { return replyEmailEnabled; }
    public void setReplyEmailEnabled(Boolean replyEmailEnabled) { this.replyEmailEnabled = replyEmailEnabled; }
    public Boolean getPublicReplyEnabled() { return publicReplyEnabled; }
    public void setPublicReplyEnabled(Boolean publicReplyEnabled) { this.publicReplyEnabled = publicReplyEnabled; }
    public Boolean getSatisfactionEnabled() { return satisfactionEnabled; }
    public void setSatisfactionEnabled(Boolean satisfactionEnabled) { this.satisfactionEnabled = satisfactionEnabled; }
    public Integer getAutoCloseDays() { return autoCloseDays; }
    public void setAutoCloseDays(Integer autoCloseDays) { this.autoCloseDays = autoCloseDays; }
}
