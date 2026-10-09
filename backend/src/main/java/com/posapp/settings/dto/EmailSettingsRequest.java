package com.posapp.settings.dto;

/**
 * Admin > Settings > Email (Fourier: "Configure smtp for sending email"). A fourth independent
 * form on the same singleton settings row — see StoreSettingsService.updateEmailSettings's
 * javadoc.
 *
 * <p>{@code smtpPassword} is deliberately nullable/optional here, unlike every other field: the
 * frontend never has the real saved password to redisplay (see StoreSettingsDto.smtpPasswordSet),
 * so re-submitting this form with the password field left blank must mean "keep what's already
 * saved," not "clear it." Only a non-blank value here overwrites the stored one — see
 * StoreSettingsService.updateEmailSettings.
 */
public class EmailSettingsRequest {

    private boolean smtpEnabled;
    private String smtpHost;
    private Integer smtpPort = 587;
    private String smtpUsername;
    private String smtpPassword;
    private String smtpFromEmail;
    private String smtpFromName;
    private boolean smtpUseSsl;
    /** Fourier's own opt-in escape hatch for a real certificate-hostname mismatch — see StoreSettings.smtpSkipCertCheck's javadoc. */
    private boolean smtpSkipCertCheck;
    /** Fourier: "Wiring a handful of business events to that same mailer" — see StoreSettings.notificationsEmail's javadoc. */
    private String notificationsEmail;

    public boolean isSmtpEnabled() { return smtpEnabled; }
    public void setSmtpEnabled(boolean smtpEnabled) { this.smtpEnabled = smtpEnabled; }
    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }
    public Integer getSmtpPort() { return smtpPort; }
    public void setSmtpPort(Integer smtpPort) { this.smtpPort = smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String smtpUsername) { this.smtpUsername = smtpUsername; }
    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword; }
    public String getSmtpFromEmail() { return smtpFromEmail; }
    public void setSmtpFromEmail(String smtpFromEmail) { this.smtpFromEmail = smtpFromEmail; }
    public String getSmtpFromName() { return smtpFromName; }
    public void setSmtpFromName(String smtpFromName) { this.smtpFromName = smtpFromName; }
    public boolean isSmtpUseSsl() { return smtpUseSsl; }
    public void setSmtpUseSsl(boolean smtpUseSsl) { this.smtpUseSsl = smtpUseSsl; }
    public boolean isSmtpSkipCertCheck() { return smtpSkipCertCheck; }
    public void setSmtpSkipCertCheck(boolean smtpSkipCertCheck) { this.smtpSkipCertCheck = smtpSkipCertCheck; }
    public String getNotificationsEmail() { return notificationsEmail; }
    public void setNotificationsEmail(String notificationsEmail) { this.notificationsEmail = notificationsEmail; }
}
