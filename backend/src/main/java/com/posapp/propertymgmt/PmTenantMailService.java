package com.posapp.propertymgmt;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.AccountMailRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.MailSent;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.Receipt;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.ReceiptMailRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.TenantAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/** E-mails a tenant's account statement or a rent receipt to the tenant, as a PDF. */
@Service
public class PmTenantMailService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}$");

    private final PmTenantAccountService accounts;
    private final PmTenantDocuments documents;
    private final MailService mailService;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    public PmTenantMailService(PmTenantAccountService accounts, PmTenantDocuments documents, MailService mailService, PropertyManagementAccess access, AuditLogService auditLogService) {
        this.accounts = accounts;
        this.documents = documents;
        this.mailService = mailService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    /** Checks the address (the tenant's own when none is given), that mail is set up, and the message; returns the address to use. */
    private String target(String given, String tenantEmail, String message) {
        String to = given == null ? "" : given.trim();
        if (to.isEmpty() && tenantEmail != null) to = tenantEmail.trim();
        if (to.isEmpty()) throw ApiException.badRequest(I18n.t("The tenant has no e-mail address. Type one in, or add it on the tenant's page."));
        if (!EMAIL.matcher(to).matches()) throw ApiException.badRequest(I18n.t("That e-mail address does not look right: ") + to);
        if (!mailService.isConfigured()) throw ApiException.badRequest(I18n.t("E-mail is not set up yet. Set up SMTP in Settings > Email first."));
        if (message != null && message.trim().length() > 1000) throw ApiException.badRequest(I18n.t("The message is too long (at most 1000 characters)."));
        return to;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String note(String message) {
        return message == null || message.trim().isEmpty() ? "" : "<p>" + esc(message.trim()).replace("\n", "<br/>") + "</p>";
    }

    @Transactional
    public MailSent emailStatement(Long tenantId, AccountMailRequest r) {
        access.on();
        TenantAccount a = accounts.account(tenantId, r == null ? null : r.from(), r == null ? null : r.to());
        String to = target(r == null ? null : r.email(), a.tenantEmail(), r == null ? null : r.message());
        String business = documents.businessName();
        String body = I18n.t("<p>Dear ") + esc(a.tenantName()) + ",</p>" + note(r == null ? null : r.message()) + I18n.t("<p>Here is your rent statement for ") + a.from().format(PmTenantAccountService.DAY) + I18n.t(" to ")
                + a.to().format(PmTenantAccountService.DAY) + I18n.t(" (PDF attached). ") + (a.closing().signum() > 0 ? I18n.t("The balance owed is <strong>") + a.closing().toPlainString() + "</strong>"
                + (a.overdue().signum() > 0 ? I18n.t(", of which ") + a.overdue().toPlainString() + I18n.t(" is overdue") : "") + "." : I18n.t("There is nothing owed.")) + I18n.t("</p><p>Regards,<br/>") + esc(business) + "</p>";
        mailService.send(to, I18n.t("Your rent statement, ") + a.from().format(PmTenantAccountService.DAY) + I18n.t(" to ") + a.to().format(PmTenantAccountService.DAY) + " - " + business, body,
                List.of(new EmailAttachment("tenant-statement-" + a.tenantCode().toLowerCase() + ".pdf", "application/pdf", documents.statement(a, documents.currency()))));
        auditLogService.record("PM_TENANT_STATEMENT_EMAILED", "E-mailed the rent statement of " + a.tenantName() + " to " + to, "PmTenant", tenantId);
        return new MailSent(to);
    }

    @Transactional
    public MailSent emailReceipt(Long paymentId, ReceiptMailRequest r) {
        access.on();
        Receipt rc = accounts.receipt(paymentId);
        String to = target(r == null ? null : r.email(), rc.tenantEmail(), r == null ? null : r.message());
        String business = documents.businessName();
        var p = rc.payment();
        String body = I18n.t("<p>Dear ") + esc(p.tenantName()) + ",</p>" + note(r == null ? null : r.message()) + I18n.t("<p>Thank you for your payment of <strong>") + p.amount().toPlainString() + "</strong>"
                + (p.paidDate() == null ? "" : I18n.t(" on ") + p.paidDate().format(PmTenantAccountService.DAY)) + I18n.t(". Your receipt ") + esc(p.receiptNumber()) + I18n.t(" is attached (PDF).</p><p>Regards,<br/>")
                + esc(business) + "</p>";
        mailService.send(to, I18n.t("Rent receipt ") + nz(p.receiptNumber()) + " - " + business, body,
                List.of(new EmailAttachment(fileName(rc), "application/pdf", documents.receipt(rc, documents.currency()))));
        auditLogService.record("PM_RECEIPT_EMAILED", "E-mailed rent receipt " + nz(p.receiptNumber()) + " to " + to, "CustomerPayment", paymentId);
        return new MailSent(to);
    }

    static String fileName(Receipt rc) {
        String n = rc.payment().receiptNumber();
        return "receipt-" + (n == null || n.isBlank() ? String.valueOf(rc.payment().paymentId()) : n.replaceAll("[^A-Za-z0-9._-]", "_")) + ".pdf";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
