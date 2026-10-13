package com.posapp.settings.dto;

/** Admin > Subscriptions > Job settings — see the matching StoreSettings.subscription* fields. */
public class SubscriptionAutomationRequest {

    private boolean billingEnabled = true;
    /** "HH:mm". */
    private String jobTime = "06:30";
    private boolean reminderEnabled = false;
    private int reminderDays = 3;
    private boolean reminderCustomer = true;
    private boolean reminderStore = false;
    private boolean digestEnabled = false;
    /** NONE, PAUSE or CANCEL. */
    private String overdueAction = "NONE";
    private int overdueDays = 30;

    public boolean isBillingEnabled() { return billingEnabled; }
    public void setBillingEnabled(boolean v) { this.billingEnabled = v; }
    public String getJobTime() { return jobTime; }
    public void setJobTime(String v) { this.jobTime = v; }
    public boolean isReminderEnabled() { return reminderEnabled; }
    public void setReminderEnabled(boolean v) { this.reminderEnabled = v; }
    public int getReminderDays() { return reminderDays; }
    public void setReminderDays(int v) { this.reminderDays = v; }
    public boolean isReminderCustomer() { return reminderCustomer; }
    public void setReminderCustomer(boolean v) { this.reminderCustomer = v; }
    public boolean isReminderStore() { return reminderStore; }
    public void setReminderStore(boolean v) { this.reminderStore = v; }
    public boolean isDigestEnabled() { return digestEnabled; }
    public void setDigestEnabled(boolean v) { this.digestEnabled = v; }
    public String getOverdueAction() { return overdueAction; }
    public void setOverdueAction(String v) { this.overdueAction = v; }
    public int getOverdueDays() { return overdueDays; }
    public void setOverdueDays(int v) { this.overdueDays = v; }
}
