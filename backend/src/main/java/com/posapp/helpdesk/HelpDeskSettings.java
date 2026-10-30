package com.posapp.helpdesk;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * Fourier: "Once the ticket is created it should send email to the helpdesk configured
 * emails(this should be set to the helpdesk setting and the email can be a list.)" — a singleton
 * row, same "exactly one row, create-on-demand" convention as {@code
 * com.posapp.settings.StoreSettings} (not a per-something list like {@code HelpDeskCategory}).
 * Kept as its own small settings row scoped to this module, rather than adding another field to
 * the already-general {@code StoreSettings.notificationsEmail} — that one address is a single
 * store-wide alert recipient for unrelated events (low stock, requisitions, …; see {@code
 * com.posapp.alerts.AlertMailService}), while this is specifically "who watches the Help Desk
 * inbox," which is naturally more than one person and unrelated to those other alerts.
 */
@Getter
@Setter
@Entity
public class HelpDeskSettings extends BaseEntity {

    /**
     * Comma-separated list of addresses — see {@link HelpDeskSettingsService#getNotificationEmails}
     * for the parsed form every caller actually uses. Stored as one TEXT column rather than a
     * separate child table since this is a short, admin-edited list with no other fields of its
     * own (no name, no per-address active flag) — the same "plain delimited string, not a whole
     * table" choice {@code SecurityConfig.allowedOrigins} already makes for its own comma-
     * separated list. Null/blank means no one is notified on ticket creation yet — same
     * "presence is the opt-in" convention as {@code StoreSettings.notificationsEmail}.
     */
    @Column(columnDefinition = "TEXT")
    private String notificationEmails;

    /** "Allow public website": when off, the public Help Desk page (submit a ticket, check a ticket) is closed. */
    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean publicEnabled = true;

    // ---- v1.20.0 ----
    @Column(nullable = false)
    private boolean slaEnabled;
    /** Whether INTERNAL tickets get deadlines too (customer tickets always do when SLA is on). */
    @Column(nullable = false)
    private boolean slaInternal;
    @Column(nullable = false)
    private int slaResponseLow = 72;
    @Column(nullable = false)
    private int slaResponseMedium = 24;
    @Column(nullable = false)
    private int slaResponseHigh = 8;
    @Column(nullable = false)
    private int slaResponseUrgent = 2;
    @Column(nullable = false)
    private int slaResolutionLow = 240;
    @Column(nullable = false)
    private int slaResolutionMedium = 120;
    @Column(nullable = false)
    private int slaResolutionHigh = 48;
    @Column(nullable = false)
    private int slaResolutionUrgent = 8;
    @Column(nullable = false)
    private boolean escalationEnabled;
    /** Hours before a resolution deadline to warn the assignee. */
    @Column(nullable = false)
    private int warnHours = 4;
    @Column(nullable = false)
    private boolean replyEmailEnabled = true;
    @Column(nullable = false)
    private boolean publicReplyEnabled = true;
    @Column(nullable = false)
    private boolean satisfactionEnabled = true;
    /** Resolved tickets close by themselves after this many days; 0 = never. */
    @Column(nullable = false)
    private int autoCloseDays;
}
