package com.posapp.law;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawPrintDtos.MailedClient;
import com.posapp.law.dto.LawPrintDtos.Printed;
import com.posapp.law.dto.LawPrintDtos.TrustReceiptData;
import com.posapp.law.dto.LawPrintDtos.TrustStatementData;
import com.posapp.law.dto.LawTrustDtos.ReconciliationDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** The client-money papers: receipts and vouchers, the client's trust ledger statement (also e-mailed to the client) and the reconciliation sheet. */
@Service
public class LawTrustPaperService {

    private final LawTrustService trust;
    private final LawPrintFiles files;
    private final LawAccess access;
    private final MailService mailService;
    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    public LawTrustPaperService(LawTrustService trust, LawPrintFiles files, LawAccess access, MailService mailService, CustomerRepository customerRepository, AuditLogService auditLogService) {
        this.trust = trust;
        this.files = files;
        this.access = access;
        this.mailService = mailService;
        this.customerRepository = customerRepository;
        this.auditLogService = auditLogService;
    }

    private static String slug(String s) {
        return s == null ? "document" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    @Transactional(readOnly = true)
    public Printed receipt(Actor a, Long txId) {
        TrustReceiptData d = trust.receiptData(a, txId);
        String kind = d.type() == TrustType.DEPOSIT ? "receipt" : "voucher";
        return new Printed(files.trustReceipt(d), "trust-" + kind + "-" + slug(d.number()) + ".pdf");
    }

    @Transactional(readOnly = true)
    public Printed statement(Actor a, Long matterId, LocalDate from, LocalDate to) {
        TrustStatementData d = trust.statementData(a, matterId, from, to);
        return new Printed(files.trustStatement(d), "trust-statement-" + slug(d.matterNumber()) + ".pdf");
    }

    @Transactional(readOnly = true)
    public Printed reconciliation(boolean canBill, Long id) {
        ReconciliationDto r = trust.reconciliationById(canBill, id);
        return new Printed(files.reconciliation(r, trust.trustBank()), "trust-reconciliation-" + r.asOfDate() + ".pdf");
    }

    private String clientEmail(Long customerId) {
        Customer c = customerId == null ? null : customerRepository.findById(customerId).orElse(null);
        return c != null && c.getEmail() != null && !c.getEmail().isBlank() ? c.getEmail().trim() : null;
    }

    /** E-mails the client their trust ledger statement (the whole history up to today) and records it in the audit log. */
    @Transactional
    public MailedClient emailStatement(Actor a, boolean canBill, Long matterId) {
        trust.requireSender(a, canBill, matterId);
        if (!mailService.isConfigured()) throw ApiException.badRequest(I18n.t("E-mail is not set up yet. Set up SMTP in Settings > Email first."));
        LawMatter m = access.viewable(a, matterId);
        String to = clientEmail(m.getClientCustomerId());
        if (to == null) throw ApiException.badRequest(I18n.t("This client has no e-mail address on the customer record."));
        Printed pdf = statement(a, matterId, null, null);
        String business = files.businessName();
        StringBuilder body = new StringBuilder();
        body.append(I18n.t("<p>Dear ")).append(HtmlUtils.htmlEscape(m.getClientName())).append(",</p>");
        body.append(I18n.t("<p>Please find attached the statement of the money we hold for you in our client account for matter <b>")).append(HtmlUtils.htmlEscape(m.getNumber()))
                .append("</b> (").append(HtmlUtils.htmlEscape(m.getTitle())).append(").</p>");
        String contact = files.contactLine();
        if (!contact.isEmpty()) body.append(I18n.t("<p>Questions? Contact us: ")).append(HtmlUtils.htmlEscape(contact)).append("</p>");
        body.append("<p>").append(HtmlUtils.htmlEscape(business)).append("</p>");
        mailService.send(to, I18n.t("Your client account statement for ") + m.getNumber() + " - " + business, body.toString(), List.of(new EmailAttachment(pdf.fileName(), "application/pdf", pdf.content())));
        auditLogService.record("LAW_TRUST_STATEMENT_SENT", "E-mailed the client trust statement of matter " + m.getNumber() + " to " + to, "LawMatter", m.getId());
        return new MailedClient(to, I18n.t("Statement sent to ") + to + ".");
    }
}
