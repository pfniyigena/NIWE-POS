package com.posapp.law;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawBillingDtos.BillingDto;
import com.posapp.law.dto.LawBillingDtos.BillingSummary;
import com.posapp.law.dto.LawBillingDtos.FeeTermsDto;
import com.posapp.law.dto.LawDtos.MatterDto;
import com.posapp.law.dto.LawPrintDtos.ClosingData;
import com.posapp.law.dto.LawPrintDtos.CoverData;
import com.posapp.law.dto.LawPrintDtos.EngagementData;
import com.posapp.law.dto.LawPrintDtos.MailedClient;
import com.posapp.law.dto.LawPrintDtos.PaperChecks;
import com.posapp.law.dto.LawPrintDtos.PaperRequest;
import com.posapp.law.dto.LawPrintDtos.Printed;
import com.posapp.law.dto.LawPrintDtos.SavedPaper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The papers of a matter: the file cover sheet, the engagement and fee letter and the closing letter. Each can be printed, kept in the matter's documents or
 * (the two letters) e-mailed to the client. The cover sheet is for anyone who may see the matter; the letters are for the team.
 */
@Service
public class LawPaperService {

    public enum Kind {
        COVER(I18n.t("Cover sheet"), DocCategory.OTHER, false), ENGAGEMENT(I18n.t("Engagement letter"), DocCategory.CONTRACT, true), CLOSING(I18n.t("Closing letter"), DocCategory.CORRESPONDENCE, true);

        final String label;
        final DocCategory category;
        final boolean toClient;

        Kind(String label, DocCategory category, boolean toClient) {
            this.label = label;
            this.category = category;
            this.toClient = toClient;
        }

        public static Kind of(String text) {
            try {
                return Kind.valueOf(text.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                throw ApiException.notFound(I18n.t("Unknown paper."));
            }
        }
    }

    private final LawAccess access;
    private final LawService lawService;
    private final LawBillingService billing;
    private final LawTrustService trust;
    private final LawPrintFiles files;
    private final LawDocumentService documents;
    private final MailService mailService;
    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    public LawPaperService(LawAccess access, LawService lawService, LawBillingService billing, LawTrustService trust, LawPrintFiles files, LawDocumentService documents,
                           MailService mailService, CustomerRepository customerRepository, AuditLogService auditLogService) {
        this.access = access;
        this.lawService = lawService;
        this.billing = billing;
        this.trust = trust;
        this.files = files;
        this.documents = documents;
        this.mailService = mailService;
        this.customerRepository = customerRepository;
        this.auditLogService = auditLogService;
    }

    private static String slug(String s) {
        return s == null ? "paper" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String clientEmail(Long customerId) {
        Customer c = customerId == null ? null : customerRepository.findById(customerId).orElse(null);
        return c != null && c.getEmail() != null && !c.getEmail().isBlank() ? c.getEmail().trim() : null;
    }

    private LawMatter matterFor(Actor a, Long id, Kind kind) {
        return kind == Kind.COVER ? access.viewable(a, id) : access.workable(a, id);
    }

    private static boolean termsSet(FeeTermsDto t, BillingDto b) {
        if (t.billingType() == null) return false;
        return switch (t.billingType()) {
            case HOURLY -> (t.hourlyRate() != null && t.hourlyRate().signum() > 0) || (b.rates() != null && !b.rates().isEmpty());
            case FIXED -> t.fixedFee() != null && t.fixedFee().signum() > 0;
            case RETAINER -> t.retainerAmount() != null && t.retainerAmount().signum() > 0;
            case CONTINGENCY -> t.contingencyPercent() != null && t.contingencyPercent().signum() > 0;
            case PRO_BONO -> true;
        };
    }

    /** Things the person should look at before printing, and whether the client can be e-mailed. */
    @Transactional
    public PaperChecks checks(Actor a, Long id) {
        LawMatter m = access.viewable(a, id);
        MatterDto dto = lawService.dto(a, m);
        BillingDto b = billing.billing(a, id, false);
        BillingSummary s = b.summary();
        boolean trustOn = lawService.settingsEntity().isTrustEnabled();
        int openD = (int) dto.deadlines().stream().filter(d -> d.status() == DeadlineStatus.OPEN).count();
        int openT = (int) dto.tasks().stream().filter(t -> t.status() != TaskStatus.DONE).count();
        return new PaperChecks(m.getStatus() == MatterStatus.CLOSED, m.getStatus().name(), openD, openT, s.unbilledTime(), s.unbilledCosts(), s.outstanding(),
                trustOn ? trust.heldFor(id) : BigDecimal.ZERO, trustOn, clientEmail(m.getClientCustomerId()), mailService.isConfigured(), termsSet(b.terms(), b), access.canWork(m, a));
    }

    @Transactional
    public Printed print(Actor a, Long id, Kind kind, PaperRequest r) {
        LawMatter m = matterFor(a, id, kind);
        MatterDto dto = lawService.dto(a, m);
        boolean trustOn = lawService.settingsEntity().isTrustEnabled();
        LocalDate date = r != null && r.letterDate() != null ? r.letterDate() : LocalDate.now();
        String signer = m.getLeadName();
        byte[] pdf;
        switch (kind) {
            case COVER -> pdf = files.coverSheet(new CoverData(dto, LawAccess.name(a.user()), LocalDate.now(), trustOn, trustOn ? trust.heldFor(id) : null));
            case ENGAGEMENT -> pdf = files.engagementLetter(new EngagementData(dto, billing.billing(a, id, false), r != null ? r.scope() : null, r != null ? r.terms() : null, date, trustOn, signer));
            case CLOSING -> {
                if (m.getStatus() != MatterStatus.CLOSED) throw ApiException.conflict(I18n.t("Close the matter first. The closing letter states the final figures."));
                pdf = files.closingLetter(new ClosingData(dto, billing.billing(a, id, false), trustOn ? trust.heldFor(id) : BigDecimal.ZERO, trustOn, r != null ? r.notes() : null, date, signer));
            }
            default -> throw ApiException.notFound(I18n.t("Unknown paper."));
        }
        return new Printed(pdf, slug(kind.label) + "-" + slug(m.getNumber()) + ".pdf");
    }

    /** Keeps the paper in the matter's documents. */
    @Transactional
    public SavedPaper save(Actor a, Long id, Kind kind, PaperRequest r) {
        Printed p = print(a, id, kind, r);
        LawMatter m = access.workable(a, id);
        String name = kind.label + " " + m.getNumber() + " (" + LawPrintFiles.shortDay(r != null && r.letterDate() != null ? r.letterDate() : LocalDate.now()) + ")";
        var d = documents.saveGenerated(a, id, name, kind.category, p.fileName(), p.content(), "Made on the Papers tab");
        return new SavedPaper(d.id(), name, "Saved in the matter's documents.");
    }

    /** E-mails a letter to the client with the PDF attached. */
    @Transactional
    public MailedClient email(Actor a, Long id, Kind kind, PaperRequest r) {
        if (!kind.toClient) throw ApiException.badRequest(I18n.t("The cover sheet is for the file. It cannot be e-mailed to the client."));
        LawMatter m = access.workable(a, id);
        if (!mailService.isConfigured()) throw ApiException.badRequest(I18n.t("E-mail is not set up yet. Set up SMTP in Settings > Email first."));
        String to = clientEmail(m.getClientCustomerId());
        if (to == null) throw ApiException.badRequest(I18n.t("This client has no e-mail address on the customer record."));
        Printed p = print(a, id, kind, r);
        String business = files.businessName();
        StringBuilder body = new StringBuilder(I18n.t("<p>Dear ")).append(HtmlUtils.htmlEscape(m.getClientName())).append(",</p>");
        body.append(kind == Kind.ENGAGEMENT
                ? I18n.t("<p>Please find attached our engagement and fee letter for matter <b>") + HtmlUtils.htmlEscape(m.getNumber()) + "</b> (" + HtmlUtils.htmlEscape(m.getTitle())
                + I18n.t("). Please read it, sign it and send a copy back to us.</p>")
                : I18n.t("<p>Please find attached our letter closing matter <b>") + HtmlUtils.htmlEscape(m.getNumber()) + "</b> (" + HtmlUtils.htmlEscape(m.getTitle()) + ").</p>");
        String contact = files.contactLine();
        if (!contact.isEmpty()) body.append(I18n.t("<p>Questions? Contact us: ")).append(HtmlUtils.htmlEscape(contact)).append("</p>");
        body.append("<p>").append(HtmlUtils.htmlEscape(business)).append("</p>");
        mailService.send(to, kind.label + " " + m.getNumber() + " - " + business, body.toString(), List.of(new EmailAttachment(p.fileName(), "application/pdf", p.content())));
        auditLogService.record("LAW_PAPER_SENT", kind.label + " of matter " + m.getNumber() + " e-mailed to " + to, "LawMatter", m.getId());
        return new MailedClient(to, kind.label + I18n.t(" sent to ") + to + ".");
    }
}
