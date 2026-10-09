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
}
