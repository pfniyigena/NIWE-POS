package com.posapp.helpdesk.dto;

import java.util.List;

/** Fourier: "this should be set to the helpdesk setting and the email can be a list" — the admin form submits the list form directly; HelpDeskSettingsService does the CSV parsing/joining. */
public class HelpDeskSettingsRequest {

    private List<String> notificationEmails;

    public List<String> getNotificationEmails() { return notificationEmails; }
    public void setNotificationEmails(List<String> notificationEmails) { this.notificationEmails = notificationEmails; }
}
