package com.posapp.salessettings;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * Admin > Sales > Settings — Fourier: "Every time invoice is ISSUED send try to send email to
 * customer and to configured emails. (Add invoices receiver emails in sales settings)." A
 * singleton row, same "exactly one row, create-on-demand" convention as {@code
 * com.posapp.settings.StoreSettings}/{@code com.posapp.helpdesk.HelpDeskSettings} (not a
 * per-something list). Kept as its own small settings row scoped to the Sales module rather than
 * folded into the already-general {@code StoreSettings.notificationsEmail} — that one address is
 * a single store-wide alert recipient for unrelated events (low stock, requisitions, …; see
 * {@code com.posapp.alerts.AlertMailService}), while this is specifically "who gets copied on
 * every issued invoice," which is naturally more than one person and unrelated to those other
 * alerts — same "module-specific list, not the general one" reasoning {@code
 * HelpDeskSettings.notificationEmails} already gives for its own list.
 */
@Getter
@Setter
@Entity
public class SalesSettings extends BaseEntity {

    /**
     * Comma-separated list of addresses — see {@link SalesSettingsService
     * #getInvoiceReceiverEmailList()} for the parsed form every caller actually uses, and {@code
     * com.posapp.invoice.InvoiceMailService#notifyInvoiceIssued} for where it's actually sent (a
     * copy of every invoice email, alongside the customer's own address, the moment an invoice
     * becomes ISSUED). Stored as one TEXT column rather than a separate child table, same "plain
     * delimited string, not a whole table" choice {@code HelpDeskSettings.notificationEmails}
     * already makes for its own short, admin-edited list with no other fields of its own.
     * Null/blank means nobody extra is copied — the invoice still goes to the customer alone.
     */
    @Column(columnDefinition = "TEXT")
    private String invoiceReceiverEmails;
}
