package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.EmailAllStatementsRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.EmailStatementRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.EmailAllResult;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.StatementDto;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.StatementRow;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Sends an owner statement to the owner by e-mail, as a PDF. Only an agreed or paid statement can be sent: a draft can still change. The date and address of the last send are kept
 * on the statement, and sending again is allowed (to correct an address, or to send a reminder).
 */
@Service
public class PmStatementMailService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}$");

    private final PmStatementService statements;
    private final PmOwnerStatementRepository repository;
    private final PmStatementFiles files;
    private final MailService mailService;
    private final StoreSettingsRepository storeSettingsRepository;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;
    private final PmViews views;
    private final PmFeeInvoiceService feeInvoices;

    public PmStatementMailService(PmStatementService statements, PmOwnerStatementRepository repository, PmStatementFiles files, MailService mailService,
                                  StoreSettingsRepository storeSettingsRepository, PropertyManagementAccess access, AuditLogService auditLogService, PmViews views, PmFeeInvoiceService feeInvoices) {
        this.feeInvoices = feeInvoices;
        this.views = views;
        this.statements = statements;
        this.repository = repository;
        this.files = files;
        this.mailService = mailService;
        this.storeSettingsRepository = storeSettingsRepository;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public StatementDto email(Actor a, Long id, EmailStatementRequest r) {
        access.on();
        PmOwnerStatement st = repository.findById(id).orElseThrow(() -> ApiException.notFound("Statement not found"));
        if (st.getStatus() == StatementStatus.DRAFT) throw ApiException.conflict("Agree the statement first. A draft can still change, so it is not sent to the owner.");
        StatementDto d = statements.get(a, id);
        String to = r == null || r.to() == null ? "" : r.to().trim();
        if (to.isEmpty() && d.ownerEmail() != null) to = d.ownerEmail().trim();
        if (to.isEmpty()) throw ApiException.badRequest("The owner has no e-mail address. Type one in, or add it on the owner's page.");
        if (!EMAIL.matcher(to).matches()) throw ApiException.badRequest("That e-mail address does not look right: " + to);
        if (!mailService.isConfigured()) throw ApiException.badRequest("E-mail is not set up yet. Set up SMTP in Settings > Email first.");
        String message = r == null || r.message() == null ? "" : r.message().trim();
        if (message.length() > 1000) throw ApiException.badRequest("The message is too long (at most 1000 characters).");

        StatementRow row = d.statement();
        StoreSettings store = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        String business = store != null && store.getBusinessName() != null && !store.getBusinessName().isBlank() ? store.getBusinessName() : "Your property manager";
        String subject = "Owner statement " + row.code() + ", " + row.periodFrom() + " to " + row.periodTo() + " - " + business;
        byte[] pdf = files.build(d, false);
        List<EmailAttachment> attachments = new ArrayList<>();
        attachments.add(new EmailAttachment(files.filename(row, false), "application/pdf", pdf));
        PmFeeInvoiceService.Attachment fee = feeInvoices.attachmentFor(id);
        if (fee != null) attachments.add(new EmailAttachment(fee.fileName(), "application/pdf", fee.bytes()));
        mailService.send(to, subject, html(business, d, message), attachments);

        st.setEmailedAt(Instant.now());
        st.setEmailedTo(to);
        repository.save(st);
        auditLogService.record("PM_STATEMENT_EMAILED", "E-mailed owner statement " + row.code() + " to " + to, "PmOwnerStatement", id);
        return statements.get(a, id);
    }

    /** Most statements sent in one go, so one request does not run for minutes. Press the button again for the rest. */
    static final int MAX_BATCH = 100;

    /**
     * E-mails every agreed statement that has not been sent yet (paid ones too when {@code includePaid}, ones already sent too when {@code resend}) to its owner's address. An owner with no
     * address, or a mail server that refuses one message, never stops the others: each is counted and explained in the result.
     */
    public EmailAllResult emailAll(Actor a, EmailAllStatementsRequest r) {
        access.on();
        if (!mailService.isConfigured()) throw ApiException.badRequest("E-mail is not set up yet. Set up SMTP in Settings > Email first.");
        boolean includePaid = r != null && Boolean.TRUE.equals(r.includePaid());
        boolean resend = r != null && Boolean.TRUE.equals(r.resend());
        Set<Long> only = r == null || r.ids() == null ? null : new HashSet<>(r.ids());
        Map<Long, PmOwner> owners = views.snapshot().owners;
        List<PmOwnerStatement> candidates = new ArrayList<>(repository.findByStatus(StatementStatus.FINAL));
        if (includePaid) candidates.addAll(repository.findByStatus(StatementStatus.PAID));
        candidates.sort(Comparator.comparing(PmOwnerStatement::getCode));
        int sent = 0, skipped = 0, failed = 0, left = 0;
        List<String> notes = new ArrayList<>();
        for (PmOwnerStatement st : candidates) {
            if (only != null && !only.contains(st.getId())) continue;
            if (!resend && st.getEmailedAt() != null) { skipped++; continue; }
            PmOwner o = owners.get(st.getOwnerId());
            String who = st.getCode() + (o == null ? "" : " (" + o.getName() + ")");
            if (o == null || o.getEmail() == null || o.getEmail().isBlank()) { skipped++; notes.add(who + ": the owner has no e-mail address."); continue; }
            if (sent + failed >= MAX_BATCH) { left++; continue; }
            try {
                email(a, st.getId(), null);
                sent++;
            } catch (RuntimeException ex) {
                failed++;
                notes.add(who + ": " + ex.getMessage());
            }
        }
        if (left > 0) notes.add(left + " more are waiting. Press the button again to send them.");
        auditLogService.record("PM_STATEMENTS_EMAILED", "E-mailed " + sent + " owner statement(s) in one go" + (failed > 0 ? ", " + failed + " failed" : ""));
        return new EmailAllResult(sent, skipped, failed, notes);
    }

    private static String html(String business, StatementDto d, String message) {
        StatementRow s = d.statement();
        StringBuilder b = new StringBuilder();
        b.append("<p>Dear ").append(esc(s.ownerName())).append(",</p>");
        if (!message.isEmpty()) b.append("<p>").append(esc(message).replace("\n", "<br/>")).append("</p>");
        b.append("<p>Here is your statement <strong>").append(esc(s.code())).append("</strong> for ").append(s.periodFrom()).append(" to ").append(s.periodTo()).append(" (PDF attached).</p>");
        b.append("<table cellpadding=\"4\" style=\"border-collapse:collapse;\">");
        row(b, "Rent collected", s.rentCollected().toPlainString(), false);
        row(b, "Management fee", "- " + s.managementFee().toPlainString(), false);
        if (s.vat().signum() != 0) row(b, "VAT on the fee", "- " + s.vat().toPlainString(), false);
        row(b, "Repairs paid by you", "- " + s.repairs().toPlainString(), false);
        if (s.adjustments().signum() != 0) row(b, "Adjustments", s.adjustments().toPlainString(), false);
        row(b, s.netPayable().signum() >= 0 ? "Net payable to you" : "Net owed to us", s.netPayable().abs().toPlainString(), true);
        b.append("</table>");
        if (s.status() == StatementStatus.PAID) {
            b.append("<p>This has been paid").append(s.paidDate() == null ? "" : " on " + s.paidDate()).append(d.paidMethod() == null ? "" : " by " + esc(d.paidMethod()))
                    .append(d.paidReference() == null ? "" : " (reference " + esc(d.paidReference()) + ")").append(".</p>");
        }
        b.append("<p>Regards,<br/>").append(esc(business)).append("</p>");
        return b.toString();
    }

    private static void row(StringBuilder b, String label, String amount, boolean bold) {
        String o = bold ? "<strong>" : "", c = bold ? "</strong>" : "";
        b.append("<tr><td>").append(o).append(esc(label)).append(c).append("</td><td align=\"right\">").append(o).append(amount).append(c).append("</td></tr>");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
