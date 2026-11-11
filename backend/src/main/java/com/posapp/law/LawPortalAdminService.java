package com.posapp.law;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawPortalDtos.*;
import com.posapp.law.dto.LawPortalRequests.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The firm's side of the client portal: which matters are open to the client and what they may see, who at the client may sign in, the short
 * progress note, which deadlines the client sees, and the messages. Everything follows the matter's own access rules (see {@link LawAccess}).
 */
@Service
public class LawPortalAdminService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,255}\\.[^@\\s]{2,}$");

    private final LawAccess access;
    private final LawMatterPortalRepository portalRepository;
    private final LawPortalAccountRepository accountRepository;
    private final LawPortalMessageRepository messageRepository;
    private final LawMatterDeadlineRepository deadlineRepository;
    private final LawDocumentRepository documentRepository;
    private final LawSettingsRepository settingsRepository;
    private final MailService mailService;
    private final AuditLogService audit;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public LawPortalAdminService(LawAccess access, LawMatterPortalRepository portalRepository, LawPortalAccountRepository accountRepository,
                                 LawPortalMessageRepository messageRepository, LawMatterDeadlineRepository deadlineRepository, LawDocumentRepository documentRepository,
                                 LawSettingsRepository settingsRepository, MailService mailService, AuditLogService audit) {
        this.access = access;
        this.portalRepository = portalRepository;
        this.accountRepository = accountRepository;
        this.messageRepository = messageRepository;
        this.deadlineRepository = deadlineRepository;
        this.documentRepository = documentRepository;
        this.settingsRepository = settingsRepository;
        this.mailService = mailService;
        this.audit = audit;
    }

    private static String clean(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private boolean firmOpen() {
        return settingsRepository.findAll().stream().findFirst().map(LawSettings::isPortalEnabled).orElse(false);
    }

    private LawMatterPortal row(Long matterId) {
        return portalRepository.findByMatterId(matterId).orElseGet(() -> {
            LawMatterPortal p = new LawMatterPortal();
            p.setMatterId(matterId);
            return p;
        });
    }

    /** True when the matter is open to the client or has portal messages, so it can no longer be deleted. */
    @Transactional(readOnly = true)
    public boolean hasRecords(LawMatter m) {
        boolean open = portalRepository.findByMatterId(m.getId()).map(LawMatterPortal::isEnabled).orElse(false);
        return open || !messageRepository.findByMatterIdOrderByIdAsc(m.getId()).isEmpty();
    }

    /** Removes the portal row of a matter that is being deleted (it has no messages, see {@link #hasRecords}). */
    @Transactional
    public void deleteFor(Long matterId) {
        portalRepository.findByMatterId(matterId).ifPresent(portalRepository::delete);
    }

    static AccountDto toDto(LawPortalAccount a) {
        boolean locked = a.getLockedUntil() != null && a.getLockedUntil().isAfter(Instant.now());
        return new AccountDto(a.getId(), a.getName(), a.getEmail(), a.isActive(), a.getPasswordHash() != null, a.getInviteExpiresAt(), a.getLastLoginAt(), locked);
    }

    @Transactional(readOnly = true)
    public AdminDto view(Actor a, Long matterId) {
        LawMatter m = access.viewable(a, matterId);
        LawMatterPortal p = row(m.getId());
        List<AccountDto> accts = accountRepository.findByCustomerIdOrderByIdAsc(m.getClientCustomerId()).stream().map(LawPortalAdminService::toDto).toList();
        List<AdminDeadlineDto> dls = deadlineRepository.findByMatterIdOrderByDueDateAscDueTimeAsc(m.getId()).stream()
                .filter(d -> d.getStatus() != DeadlineStatus.CANCELLED)
                .map(d -> new AdminDeadlineDto(d.getId(), d.getTitle(), d.getType(), d.getDueDate(), d.getStatus(), d.isVisibleToClient())).toList();
        List<AdminMessageDto> msgs = messageRepository.findByMatterIdOrderByIdAsc(m.getId()).stream()
                .map(x -> new AdminMessageDto(x.getId(), x.isFromClient(), x.getSenderName(), x.getBody(), x.getCreatedAt(), x.isFromClient() ? x.isReadByStaff() : x.isReadByClient())).toList();
        long pending = documentRepository.findByMatterIdOrderByUpdatedAtDesc(m.getId()).stream().filter(d -> d.isFromClient() && d.getReviewStatus() == DocReview.PENDING).count();
        return new AdminDto(m.getId(), m.getNumber(), m.getTitle(), m.getClientName(), firmOpen(), p.isEnabled(), p.isShowDeadlines(), p.isShowDocuments(), p.isShowInvoices(),
                p.isShowTrust(), p.isAllowUpload(), p.isAllowMessages(), p.getClientUpdate(), p.getClientUpdateAt(), p.getClientUpdateBy(), access.canLead(m, a), access.canWork(m, a),
                accts, dls, msgs, messageRepository.countByMatterIdAndFromClientTrueAndReadByStaffFalse(m.getId()), pending);
    }

    @Transactional
    public AdminDto saveSettings(Actor a, Long matterId, SettingsRequest r) {
        LawMatter m = access.leadable(a, matterId);
        if (r == null) throw ApiException.badRequest(I18n.t("Nothing to change."));
        LawMatterPortal p = row(m.getId());
        if (r.enabled() != null) {
            if (r.enabled() && !firmOpen()) throw ApiException.conflict(I18n.t("The client portal is switched off for the firm. An admin switches it on in Law Firm > Settings."));
            if (r.enabled() && !m.isConflictCleared()) throw ApiException.conflict(I18n.t("Clear the conflict check before opening the matter to the client."));
            p.setEnabled(r.enabled());
        }
        if (r.showDeadlines() != null) p.setShowDeadlines(r.showDeadlines());
        if (r.showDocuments() != null) p.setShowDocuments(r.showDocuments());
        if (r.showInvoices() != null) p.setShowInvoices(r.showInvoices());
        if (r.showTrust() != null) p.setShowTrust(r.showTrust());
        if (r.allowUpload() != null) p.setAllowUpload(r.allowUpload());
        if (r.allowMessages() != null) p.setAllowMessages(r.allowMessages());
        portalRepository.save(p);
        audit.record("LAW_PORTAL_SETTINGS", "Client portal on matter " + m.getNumber() + ": " + (p.isEnabled() ? "open" : "closed") + ", deadlines=" + p.isShowDeadlines() + ", documents="
                + p.isShowDocuments() + ", invoices=" + p.isShowInvoices() + ", trust=" + p.isShowTrust() + ", upload=" + p.isAllowUpload() + ", messages=" + p.isAllowMessages(),
                "LawMatter", m.getId());
        return view(a, matterId);
    }

    @Transactional
    public AdminDto postUpdate(Actor a, Long matterId, UpdateRequest r) {
        LawMatter m = access.workable(a, matterId);
        String text = r == null ? null : clean(r.text());
        if (text != null && text.length() > 2000) throw ApiException.badRequest(I18n.t("Keep the note under 2000 characters."));
        LawMatterPortal p = row(m.getId());
        p.setClientUpdate(text);
        p.setClientUpdateAt(text == null ? null : Instant.now());
        p.setClientUpdateBy(text == null ? null : LawAccess.name(a.user()));
        portalRepository.save(p);
        audit.record("LAW_PORTAL_UPDATE", "Progress note for the client on matter " + m.getNumber() + (text == null ? " cleared" : " posted"), "LawMatter", m.getId());
        return view(a, matterId);
    }

    @Transactional
    public AdminDto setDeadlineVisible(Actor a, Long deadlineId, VisibleRequest r) {
        LawMatterDeadline d = deadlineRepository.findById(deadlineId).orElseThrow(() -> ApiException.notFound(I18n.t("Deadline not found.")));
        LawMatter m = access.workable(a, d.getMatterId());
        if (r == null || r.visible() == null) throw ApiException.badRequest(I18n.t("Choose whether the client sees it."));
        d.setVisibleToClient(r.visible());
        deadlineRepository.save(d);
        audit.record("LAW_PORTAL_DEADLINE", "Deadline '" + d.getTitle() + "' on matter " + m.getNumber() + (r.visible() ? " shown to" : " hidden from") + " the client", "LawMatterDeadline", d.getId());
        return view(a, m.getId());
    }

    // ------------------------------------------------------------------ accounts

    private InviteResultDto issue(LawPortalAccount acc, LawMatter m) {
        String token = LawPortalAuthService.newToken();
        acc.setInviteTokenHash(LawPortalAuthService.hash(token));
        acc.setInviteExpiresAt(Instant.now().plus(LawPortalAuthService.INVITE_DAYS, ChronoUnit.DAYS));
        accountRepository.save(acc);
        String link = frontendUrl + "/portal/accept?token=" + token;
        boolean sent = false;
        String note;
        if (mailService.isConfigured()) {
            try {
                String html = I18n.t("<p>Hello ") + esc(acc.getName()) + I18n.t(",</p><p>You have been given access to the client portal to follow your matter with us. "
                        + "Open the link below to choose your password. It works once and expires in ") + LawPortalAuthService.INVITE_DAYS + I18n.t(" days.</p>"
                        + "<p><a href=\"") + link + I18n.t("\">Open the client portal</a></p><p>If you were not expecting this, you can ignore this message.</p>");
                mailService.send(acc.getEmail(), I18n.t("Your client portal access"), html, List.of());
                sent = true;
                note = I18n.t("The invitation was sent by e-mail.");
            } catch (Exception e) {
                note = I18n.t("The e-mail could not be sent (") + e.getMessage() + I18n.t("). Give the client the link yourself.");
            }
        } else {
            note = I18n.t("E-mail is not set up, so nothing was sent. Give the client the link yourself.");
        }
        audit.record("LAW_PORTAL_INVITE", "Portal invitation issued to " + acc.getEmail() + " for matter " + m.getNumber() + (sent ? " (e-mailed)" : ""), "LawPortalAccount", acc.getId());
        return new InviteResultDto(toDto(acc), link, sent, note);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Transactional
    public InviteResultDto invite(Actor a, Long matterId, InviteRequest r) {
        LawMatter m = access.leadable(a, matterId);
        if (!firmOpen()) throw ApiException.conflict(I18n.t("The client portal is switched off for the firm. An admin switches it on in Law Firm > Settings."));
        if (r == null || clean(r.name()) == null) throw ApiException.badRequest(I18n.t("Enter the person's name."));
        String email = LawPortalAuthService.normalise(r.email());
        if (!EMAIL.matcher(email).matches()) throw ApiException.badRequest(I18n.t("Enter a valid e-mail address."));
        LawPortalAccount acc = accountRepository.findByEmail(email).orElse(null);
        if (acc != null && !acc.getCustomerId().equals(m.getClientCustomerId())) throw ApiException.conflict(I18n.t("This e-mail address is already used for another client's portal account."));
        if (acc == null) {
            acc = new LawPortalAccount();
            acc.setCustomerId(m.getClientCustomerId());
            acc.setEmail(email);
            acc.setCreatedByName(LawAccess.name(a.user()));
        }
        acc.setName(clean(r.name()));
        acc.setActive(true);
        return issue(acc, m);
    }

    private LawPortalAccount accountOf(LawMatter m, Long accountId) {
        LawPortalAccount acc = accountRepository.findById(accountId).orElseThrow(() -> ApiException.notFound(I18n.t("Account not found.")));
        if (!acc.getCustomerId().equals(m.getClientCustomerId())) throw ApiException.notFound(I18n.t("Account not found."));
        return acc;
    }

    /** A new one-time link (also the way to reset a forgotten password). The old password keeps working until the person uses the link. */
    @Transactional
    public InviteResultDto reinvite(Actor a, Long matterId, Long accountId) {
        LawMatter m = access.leadable(a, matterId);
        if (!firmOpen()) throw ApiException.conflict(I18n.t("The client portal is switched off for the firm."));
        LawPortalAccount acc = accountOf(m, accountId);
        if (!acc.isActive()) throw ApiException.conflict(I18n.t("Switch the account on first."));
        return issue(acc, m);
    }

    @Transactional
    public AdminDto setActive(Actor a, Long matterId, Long accountId, ActiveRequest r) {
        LawMatter m = access.leadable(a, matterId);
        LawPortalAccount acc = accountOf(m, accountId);
        if (r == null || r.active() == null) throw ApiException.badRequest(I18n.t("Choose on or off."));
        acc.setActive(r.active());
        if (!r.active()) {
            acc.setSessionsValidFrom(Instant.now());
            acc.setInviteTokenHash(null);
            acc.setInviteExpiresAt(null);
        }
        acc.setFailedAttempts(0);
        acc.setLockedUntil(null);
        accountRepository.save(acc);
        audit.record("LAW_PORTAL_ACCOUNT", "Portal account " + acc.getEmail() + (r.active() ? " switched on" : " switched off"), "LawPortalAccount", acc.getId());
        return view(a, matterId);
    }

    // ------------------------------------------------------------------ messages

    @Transactional
    public AdminDto reply(Actor a, Long matterId, MessageRequest r) {
        LawMatter m = access.workable(a, matterId);
        LawMatterPortal p = row(m.getId());
        if (!p.isEnabled() || !p.isAllowMessages()) throw ApiException.conflict(I18n.t("Messages are not switched on for this matter."));
        String body = r == null ? null : clean(r.body());
        if (body == null) throw ApiException.badRequest(I18n.t("Write a message."));
        if (body.length() > 2000) throw ApiException.badRequest(I18n.t("Keep the message under 2000 characters."));
        LawPortalMessage x = new LawPortalMessage();
        x.setMatterId(m.getId());
        x.setFromClient(false);
        x.setSenderUserId(a.user().getId());
        x.setSenderName(LawAccess.name(a.user()));
        x.setBody(body);
        x.setReadByStaff(true);
        messageRepository.save(x);
        audit.record("LAW_PORTAL_MESSAGE", "Message to the client on matter " + m.getNumber(), "LawMatter", m.getId());
        if (mailService.isConfigured()) {
            for (LawPortalAccount acc : accountRepository.findByCustomerIdOrderByIdAsc(m.getClientCustomerId())) {
                if (!acc.isActive() || acc.getPasswordHash() == null) continue;
                try {
                    mailService.send(acc.getEmail(), I18n.t("New message about your matter ") + m.getNumber(),
                            I18n.t("<p>Hello ") + esc(acc.getName()) + I18n.t(",</p><p>You have a new message from the firm about your matter ") + esc(m.getNumber())
                                    + I18n.t(". Sign in to the client portal to read it:</p><p><a href=\"") + frontendUrl + "/portal\">Open the client portal</a></p>", List.of());
                } catch (Exception ignored) {
                    // the message is saved; the e-mail is only a nudge
                }
            }
        }
        return view(a, matterId);
    }

    @Transactional
    public AdminDto markRead(Actor a, Long matterId) {
        LawMatter m = access.workable(a, matterId);
        for (LawPortalMessage x : messageRepository.findByMatterIdOrderByIdAsc(m.getId())) {
            if (x.isFromClient() && !x.isReadByStaff()) {
                x.setReadByStaff(true);
                messageRepository.save(x);
            }
        }
        return view(a, matterId);
    }
}
