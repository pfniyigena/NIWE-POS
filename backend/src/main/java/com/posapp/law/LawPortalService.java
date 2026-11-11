package com.posapp.law;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.credit.CustomerCreditService;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.MailService;
import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceStatus;
import com.posapp.law.LawDocumentService.Download;
import com.posapp.law.dto.LawPortalDtos.*;
import com.posapp.law.dto.LawPortalRequests.MessageRequest;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a signed-in client sees and does in the portal. Every call is checked three ways: the account is still valid (done on each request by
 * {@link LawPortalAuthService#authenticate}), the matter belongs to the account's own client, and the firm opened that matter to the portal.
 * Anything else answers "not found", so a client cannot tell whether another client's matter exists.
 */
@Service
public class LawPortalService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int MAX_PENDING_UPLOADS = 20;
    private static final int MAX_UNANSWERED = 30;

    private final LawPortalAccountRepository accountRepository;
    private final LawMatterRepository matterRepository;
    private final LawMatterPortalRepository portalRepository;
    private final LawMatterDeadlineRepository deadlineRepository;
    private final LawDocumentRepository documentRepository;
    private final LawDocumentService documents;
    private final LawBillRepository billRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerCreditService creditService;
    private final LawTrustTransactionRepository trustRepository;
    private final LawPortalMessageRepository messageRepository;
    private final LawSettingsRepository settingsRepository;
    private final CustomerRepository customerRepository;
    private final StoreSettingsRepository storeSettingsRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final AuditLogService audit;

    public LawPortalService(LawPortalAccountRepository accountRepository, LawMatterRepository matterRepository, LawMatterPortalRepository portalRepository,
                            LawMatterDeadlineRepository deadlineRepository, LawDocumentRepository documentRepository, LawDocumentService documents, LawBillRepository billRepository,
                            InvoiceRepository invoiceRepository, CustomerCreditService creditService, LawTrustTransactionRepository trustRepository,
                            LawPortalMessageRepository messageRepository, LawSettingsRepository settingsRepository, CustomerRepository customerRepository,
                            StoreSettingsRepository storeSettingsRepository, UserRepository userRepository, MailService mailService, AuditLogService audit) {
        this.accountRepository = accountRepository;
        this.matterRepository = matterRepository;
        this.portalRepository = portalRepository;
        this.deadlineRepository = deadlineRepository;
        this.documentRepository = documentRepository;
        this.documents = documents;
        this.billRepository = billRepository;
        this.invoiceRepository = invoiceRepository;
        this.creditService = creditService;
        this.trustRepository = trustRepository;
        this.messageRepository = messageRepository;
        this.settingsRepository = settingsRepository;
        this.customerRepository = customerRepository;
        this.storeSettingsRepository = storeSettingsRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.audit = audit;
    }

    private static final class Open {
        final LawMatter matter;
        final LawMatterPortal portal;

        Open(LawMatter matter, LawMatterPortal portal) {
            this.matter = matter;
            this.portal = portal;
        }
    }

    private void requireAccount(LawPortalPrincipal p) {
        LawPortalAccount a = accountRepository.findById(p.accountId()).orElseThrow(() -> ApiException.forbidden(I18n.t("Sign in again.")));
        if (!a.isActive() || !a.getCustomerId().equals(p.customerId())) throw ApiException.forbidden(I18n.t("Sign in again."));
    }

    /** The matter, if it is this client's and open to the portal. Anything else is "not found". */
    private Open open(LawPortalPrincipal p, Long matterId) {
        requireAccount(p);
        LawMatter m = matterRepository.findById(matterId).orElseThrow(() -> ApiException.notFound(I18n.t("Matter not found.")));
        if (!p.customerId().equals(m.getClientCustomerId())) throw ApiException.notFound(I18n.t("Matter not found."));
        LawMatterPortal portal = portalRepository.findByMatterId(m.getId()).filter(LawMatterPortal::isEnabled).orElseThrow(() -> ApiException.notFound(I18n.t("Matter not found.")));
        return new Open(m, portal);
    }

    private StoreSettings firm() {
        return storeSettingsRepository.findAll().stream().findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ views

    @Transactional(readOnly = true)
    public MeDto me(LawPortalPrincipal p) {
        requireAccount(p);
        Customer c = customerRepository.findById(p.customerId()).orElse(null);
        StoreSettings s = firm();
        LocalDate today = LocalDate.now();
        List<MatterRowDto> rows = new ArrayList<>();
        for (LawMatter m : matterRepository.findByClientCustomerIdOrderByOpenedDateDescIdDesc(p.customerId())) {
            LawMatterPortal portal = portalRepository.findByMatterId(m.getId()).filter(LawMatterPortal::isEnabled).orElse(null);
            if (portal == null) continue;
            LocalDate next = null;
            if (portal.isShowDeadlines()) {
                next = deadlineRepository.findByMatterIdOrderByDueDateAscDueTimeAsc(m.getId()).stream()
                        .filter(d -> d.isVisibleToClient() && d.getStatus() == DeadlineStatus.OPEN && !d.getDueDate().isBefore(today)).map(LawMatterDeadline::getDueDate).findFirst().orElse(null);
            }
            rows.add(new MatterRowDto(m.getId(), m.getNumber(), m.getTitle(), m.getStatus(), m.getPracticeArea(), m.getLeadName(), m.getOpenedDate(),
                    portal.isAllowMessages() ? messageRepository.countByMatterIdAndFromClientFalseAndReadByClientFalse(m.getId()) : 0, next));
        }
        return new MeDto(p.name(), p.email(), c != null ? c.getName() : "", s != null ? s.getBusinessName() : null, s != null ? s.getPhone() : null, s != null ? s.getEmail() : null, rows);
    }

    @Transactional(readOnly = true)
    public MatterDto matter(LawPortalPrincipal p, Long matterId) {
        Open o = open(p, matterId);
        LawMatter m = o.matter;
        LawMatterPortal portal = o.portal;
        FeaturesDto f = new FeaturesDto(portal.isShowDeadlines(), portal.isShowDocuments(), portal.isShowInvoices(), portal.isShowTrust(), portal.isAllowUpload(), portal.isAllowMessages());

        List<DeadlineDto> dls = portal.isShowDeadlines()
                ? deadlineRepository.findByMatterIdOrderByDueDateAscDueTimeAsc(m.getId()).stream()
                .filter(d -> d.isVisibleToClient() && d.getStatus() != DeadlineStatus.CANCELLED)
                .map(d -> new DeadlineDto(d.getTitle(), d.getType(), d.getDueDate(), d.getDueTime(), d.getStatus())).toList()
                : List.of();

        List<LawDocument> docs = documentRepository.findByMatterIdOrderByUpdatedAtDesc(m.getId());
        List<DocumentDto> shared = portal.isShowDocuments()
                ? docs.stream().filter(d -> d.isSharedWithClient() && !d.isConfidential() && !d.isArchived() && !d.isFromClient())
                .map(d -> new DocumentDto(d.getId(), d.getName(), d.getCategory(), d.getFileName(), d.getSizeBytes(), d.getCurrentVersion(), d.getUpdatedAt())).toList()
                : List.of();
        List<UploadDto> uploads = portal.isAllowUpload()
                ? docs.stream().filter(LawDocument::isFromClient)
                .map(d -> new UploadDto(d.getId(), d.getName(), d.getFileName(), d.getSizeBytes(), d.getReviewStatus(), d.getReviewNote(), d.getCreatedAt())).toList()
                : List.of();

        List<InvoiceDto> invoices = portal.isShowInvoices() ? invoices(m, p.customerId()) : List.of();
        TrustDto trust = portal.isShowTrust() && settingsRepository.findAll().stream().findFirst().map(LawSettings::isTrustEnabled).orElse(false) ? trust(m) : null;

        List<MessageDto> msgs = List.of();
        if (portal.isAllowMessages()) {
            msgs = messageRepository.findByMatterIdOrderByIdAsc(m.getId()).stream().map(x -> new MessageDto(x.getId(), x.isFromClient(), x.getSenderName(), x.getBody(), x.getCreatedAt())).toList();
        }
        return new MatterDto(m.getId(), m.getNumber(), m.getTitle(), m.getStatus(), m.getPracticeArea(), m.getJurisdiction(), m.getCourtOrForum(), m.getCourtCaseNumber(), m.getLeadName(),
                m.getOpenedDate(), m.getClosedDate(), portal.getClientUpdate(), portal.getClientUpdateAt(), f, dls, shared, uploads, invoices, trust, msgs);
    }

    private List<InvoiceDto> invoices(LawMatter m, Long customerId) {
        List<InvoiceDto> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (LawBill b : billRepository.findByMatterIdOrderByIdDesc(m.getId())) {
            if (b.getStatus() != BillStatus.ACTIVE || b.getInvoiceId() == null || !seen.add(b.getInvoiceId())) continue;
            Invoice inv = invoiceRepository.findById(b.getInvoiceId()).orElse(null);
            // only issued invoices addressed to this client: never a draft, a voided one, or one for somebody else
            if (inv == null || inv.getStatus() != InvoiceStatus.ISSUED || !customerId.equals(inv.getCustomerId())) continue;
            out.add(new InvoiceDto(inv.getInvoiceNumber(), inv.getInvoiceDate(), inv.getDueDate(), inv.getTotalAmount(), creditService.amountPaidForInvoice(inv), creditService.outstandingForInvoice(inv)));
        }
        return out;
    }

    private static String kind(TrustType t) {
        return switch (t) {
            case DEPOSIT -> I18n.t("Money received");
            case PAYOUT -> I18n.t("Payment made for you");
            case FEE_TRANSFER -> I18n.t("Fees paid from your money");
            case DEPOSIT_REVERSAL -> I18n.t("Deposit returned");
        };
    }

    private TrustDto trust(LawMatter m) {
        BigDecimal running = ZERO;
        List<TrustEntryDto> entries = new ArrayList<>();
        for (LawTrustTransaction t : trustRepository.findByMatterIdOrderByIdAsc(m.getId())) {
            if (t.getStatus() != TrustStatus.POSTED) continue;
            running = running.add(t.getType() == TrustType.DEPOSIT ? t.getAmount() : t.getAmount().negate());
            entries.add(new TrustEntryDto(t.getTxDate(), kind(t.getType()), t.getAmount(), t.getReference(), t.getPurpose(), running));
        }
        BigDecimal balance = running;
        entries.sort(Comparator.comparing(TrustEntryDto::date).reversed());
        return new TrustDto(balance, entries);
    }

    // ------------------------------------------------------------------ documents

    @Transactional
    public Download download(LawPortalPrincipal p, Long documentId) {
        LawDocument d = documentRepository.findById(documentId).orElseThrow(() -> ApiException.notFound(I18n.t("Document not found.")));
        Open o = open(p, d.getMatterId());
        boolean ok = d.isFromClient()
                ? o.portal.isAllowUpload()
                : (o.portal.isShowDocuments() && d.isSharedWithClient() && !d.isConfidential() && !d.isArchived());
        if (!ok) throw ApiException.notFound(I18n.t("Document not found."));
        audit.record("LAW_PORTAL_DOWNLOAD", "Client " + p.email() + " downloaded '" + d.getName() + "' from matter " + o.matter.getNumber(), "LawDocument", d.getId());
        return documents.currentFile(d);
    }

    @Transactional
    public UploadDto upload(LawPortalPrincipal p, Long matterId, MultipartFile file, String name) {
        Open o = open(p, matterId);
        if (!o.portal.isAllowUpload()) throw ApiException.conflict(I18n.t("Sending documents is not switched on for this matter."));
        if (o.matter.getStatus() == MatterStatus.CLOSED) throw ApiException.conflict(I18n.t("This matter is closed."));
        if (file == null || file.isEmpty()) throw ApiException.badRequest(I18n.t("Choose a file to send."));
        if (file.getSize() > LawDocumentService.MAX_BYTES) throw ApiException.badRequest(I18n.t("That file is too big. The limit is 25 MB."));
        long pending = documentRepository.findByMatterIdOrderByUpdatedAtDesc(o.matter.getId()).stream().filter(d -> d.isFromClient() && d.getReviewStatus() == DocReview.PENDING).count();
        if (pending >= MAX_PENDING_UPLOADS) throw ApiException.conflict(I18n.t("Several documents are still waiting for the firm to look at them. Please wait before sending more."));
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest(I18n.t("The file could not be read."));
        }
        LawDocument d = documents.clientUpload(o.matter, p.name(), data, file.getOriginalFilename(), file.getContentType(), name);
        audit.record("LAW_PORTAL_UPLOAD", "Client " + p.email() + " sent '" + d.getName() + "' on matter " + o.matter.getNumber(), "LawDocument", d.getId());
        notifyLead(o.matter, I18n.t("A client sent a document on ") + o.matter.getNumber(), "<p>" + esc(p.name()) + I18n.t(" sent the document '") + esc(d.getName()) + I18n.t("' through the client portal. It is waiting for your review on the matter's Documents tab.</p>"));
        return new UploadDto(d.getId(), d.getName(), d.getFileName(), d.getSizeBytes(), d.getReviewStatus(), d.getReviewNote(), d.getCreatedAt());
    }

    // ------------------------------------------------------------------ messages

    @Transactional
    public List<MessageDto> post(LawPortalPrincipal p, Long matterId, MessageRequest r) {
        Open o = open(p, matterId);
        if (!o.portal.isAllowMessages()) throw ApiException.conflict(I18n.t("Messages are not switched on for this matter."));
        String body = r == null || r.body() == null ? null : r.body().trim();
        if (body == null || body.isEmpty()) throw ApiException.badRequest(I18n.t("Write a message."));
        if (body.length() > 2000) throw ApiException.badRequest(I18n.t("Keep the message under 2000 characters."));
        if (messageRepository.countByMatterIdAndFromClientTrueAndReadByStaffFalse(o.matter.getId()) >= MAX_UNANSWERED) {
            throw ApiException.conflict(I18n.t("The firm has not read your earlier messages yet. Please wait for a reply."));
        }
        LawPortalMessage x = new LawPortalMessage();
        x.setMatterId(o.matter.getId());
        x.setFromClient(true);
        x.setPortalAccountId(p.accountId());
        x.setSenderName(p.name());
        x.setBody(body);
        x.setReadByClient(true);
        messageRepository.save(x);
        audit.record("LAW_PORTAL_MESSAGE_IN", "Client " + p.email() + " wrote on matter " + o.matter.getNumber(), "LawMatter", o.matter.getId());
        notifyLead(o.matter, I18n.t("New client message on ") + o.matter.getNumber(), "<p>" + esc(p.name()) + I18n.t(" wrote to you through the client portal. Open the matter's Client portal tab to read and answer.</p>"));
        return messageRepository.findByMatterIdOrderByIdAsc(o.matter.getId()).stream().map(m -> new MessageDto(m.getId(), m.isFromClient(), m.getSenderName(), m.getBody(), m.getCreatedAt())).toList();
    }

    @Transactional
    public void markRead(LawPortalPrincipal p, Long matterId) {
        Open o = open(p, matterId);
        for (LawPortalMessage x : messageRepository.findByMatterIdOrderByIdAsc(o.matter.getId())) {
            if (!x.isFromClient() && !x.isReadByClient()) {
                x.setReadByClient(true);
                messageRepository.save(x);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void notifyLead(LawMatter m, String subject, String html) {
        try {
            if (!mailService.isConfigured() || m.getLeadUserId() == null) return;
            AppUser lead = userRepository.findById(m.getLeadUserId()).orElse(null);
            if (lead == null || lead.getEmail() == null || lead.getEmail().isBlank()) return;
            mailService.send(lead.getEmail(), subject, html, List.of());
        } catch (Exception ignored) {
            // the record is saved; the e-mail is only a nudge
        }
    }
}
