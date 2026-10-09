package com.posapp.invoice;

import com.posapp.audit.AuditLogService;
import com.posapp.email.DocumentPdfService;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.salessettings.SalesSettingsService;
import com.posapp.settings.StoreSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fourier: "Every time invoice is ISSUED send try to send email to customer and to configured
 * emails. (Add invoices receiver emails in sales settings)" — the "try to" is taken literally:
 * this is the exact same "best-effort, never block the caller" pattern as {@code
 * com.posapp.helpdesk.HelpDeskMailService}/{@code com.posapp.alerts.AlertMailService} — every
 * public method here is {@code @Async} and swallows its own exceptions (per recipient, so one bad
 * address never stops the others), no-ops silently whenever there's nobody to send to or {@link
 * MailService#isConfigured()} is false, and is never allowed to fail the invoice-issuing
 * transaction it's fired from — see {@code InvoiceService}'s own call sites (right after {@code
 * issue}/{@code generate}/{@code generateForSubscription} commit).
 *
 * <p>Deliberately takes an already-built {@link InvoiceDto} (never the {@link Invoice} entity) —
 * by the time this {@code @Async} method actually runs, the transaction that issued the invoice
 * may well have already committed and the entity detached, so reading any of its lazy
 * collections here would risk a {@code LazyInitializationException}; the DTO is already a plain,
 * fully-resolved snapshot with nothing left to lazily load. Same reasoning for taking a plain
 * {@link StoreSettings} rather than re-fetching it via {@code StoreSettingsService} on this
 * thread.
 */
@Service
public class InvoiceMailService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceMailService.class);

    private final MailService mailService;
    private final DocumentPdfService documentPdfService;
    private final SalesSettingsService salesSettingsService;
    private final AuditLogService auditLogService;
    private final com.posapp.branch.BranchService branchService;

    public InvoiceMailService(MailService mailService,
                               DocumentPdfService documentPdfService,
                               SalesSettingsService salesSettingsService,
                               AuditLogService auditLogService,
                               com.posapp.branch.BranchService branchService) {
        this.mailService = mailService;
        this.documentPdfService = documentPdfService;
        this.salesSettingsService = salesSettingsService;
        this.auditLogService = auditLogService;
        this.branchService = branchService;
    }

    /** Fired once from InvoiceService right after an invoice becomes ISSUED — see this class's own javadoc for which call sites that is. */
    @Async
    public void notifyInvoiceIssued(InvoiceDto invoice, StoreSettings settings) {
        if (!mailService.isConfigured()) {
            return;
        }
        // Fourier: "send ... to customer and to configured emails" — the customer's own address
        // (already resolved onto the DTO by InvoiceService.toDto) plus the Sales settings list,
        // deduped in case the customer's own address happens to also be on that list.
        Set<String> recipients = new LinkedHashSet<>();
        if (invoice.customerEmail() != null && !invoice.customerEmail().isBlank()) {
            recipients.add(invoice.customerEmail().trim());
        }
        recipients.addAll(salesSettingsService.getInvoiceReceiverEmailList());
        if (recipients.isEmpty()) {
            return;
        }

        byte[] pdf;
        try {
            pdf = documentPdfService.invoicePdf(invoice, settings,
                    invoice.branchId() != null ? branchService.receiptInfo(invoice.branchId()) : null);
        } catch (Exception ex) {
            log.warn("Failed to build PDF for invoice {} — not auto-emailing: {}", invoice.invoiceNumber(), ex.getMessage());
            return;
        }
        String subject = "Invoice " + invoice.invoiceNumber();
        String html = "<p>Hello " + escapeHtml(invoice.customerName()) + ",</p>"
                + "<p>Please find attached invoice " + invoice.invoiceNumber() + " for " + invoice.totalAmount() + ".</p>";
        List<EmailAttachment> attachments = List.of(new EmailAttachment(invoice.invoiceNumber() + ".pdf", "application/pdf", pdf));

        for (String to : recipients) {
            try {
                mailService.send(to, subject, html, attachments);
                auditLogService.record("INVOICE_EMAIL_AUTO_SENT",
                        "Automatically emailed invoice " + invoice.invoiceNumber() + " to " + to + " on issue",
                        "Invoice", invoice.id());
            } catch (Exception ex) {
                // Fourier: "send try to send" — one recipient's bad/unreachable address, or the
                // SMTP server rejecting that particular send, never stops the others and never
                // bubbles back to whoever called InvoiceService — the invoice is already ISSUED
                // and posted by the time this runs regardless.
                log.warn("Failed to auto-email invoice {} to {}: {}", invoice.invoiceNumber(), to, ex.getMessage());
            }
        }
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
