package com.posapp.helpdesk;

import com.posapp.email.MailService;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fourier: "Once the ticket is created it should send email to the helpdesk configured emails...
 * Once the ticket is assigned to a staff, staff and the ticket creator should receive emails
 * (notifications). Once resolved also send notification by email" — the Help Desk module's own
 * mailer, built on the exact same "best-effort, never block the caller" pattern as {@code
 * com.posapp.alerts.AlertMailService}: every public method here is {@code @Async} and swallows
 * its own exceptions, and no-ops silently whenever there's no address to send to or {@link
 * MailService#isConfigured()} is false. Kept as its own service rather than added to {@code
 * AlertMailService} since its recipients are never the single store-wide {@code
 * StoreSettings.notificationsEmail} address — they're either the {@link HelpDeskSettings} list
 * (ticket creation) or people tied to the specific ticket (the assignee, the requester/creator).
 *
 * <p>"The ticket creator" resolves to {@link HelpDeskTicket#getRequesterEmail()} when the ticket
 * has one (every guest-created ticket, and any staff-created one where a requester email was
 * given); otherwise, for an internally-created ticket with no requester email at all, it falls
 * back to the {@code AppUser.email} of whoever created it ({@link HelpDeskTicket
 * #getCreatedByUserId()}). A staff member with no {@code AppUser.email} on file simply gets no
 * email, the same silent-no-op {@code AlertMailService} already accepts for an unconfigured
 * recipient — this never blocks assignment or resolution.
 */
@Service
public class HelpDeskMailService {

    private static final Logger log = LoggerFactory.getLogger(HelpDeskMailService.class);

    private final MailService mailService;
    private final HelpDeskSettingsService settingsService;
    private final UserRepository userRepository;

    public HelpDeskMailService(MailService mailService, HelpDeskSettingsService settingsService, UserRepository userRepository) {
        this.mailService = mailService;
        this.settingsService = settingsService;
        this.userRepository = userRepository;
    }

    /** Fired at the end of HelpDeskTicketService.createByStaff and .createByGuest. */
    @Async
    public void notifyTicketCreated(HelpDeskTicket ticket) {
        List<String> recipients = settingsService.getNotificationEmailList();
        if (recipients.isEmpty()) {
            return;
        }
        String subject = "New help desk ticket: " + ticket.getTicketNumber();
        String html = "<p>A new " + (ticket.getType() == HelpDeskTicketType.CUSTOMER ? "customer" : "internal")
                + " ticket has been raised.</p>"
                + "<p><strong>" + escapeHtml(ticket.getSubject()) + "</strong></p>"
                + "<p>Ticket: " + escapeHtml(ticket.getTicketNumber()) + "</p>"
                + "<p>Priority: " + ticket.getPriority() + "</p>"
                + (ticket.getRequesterName() != null ? "<p>From: " + escapeHtml(ticket.getRequesterName()) + "</p>" : "");
        for (String to : recipients) {
            send(to, subject, html);
        }
    }

    /** Fired from HelpDeskTicketService.assign only when the assignee actually changes to a new non-null value. */
    @Async
    public void notifyTicketAssigned(HelpDeskTicket ticket) {
        String subject = "Help desk ticket assigned: " + ticket.getTicketNumber();
        String html = "<p>Ticket " + escapeHtml(ticket.getTicketNumber()) + " (\"" + escapeHtml(ticket.getSubject())
                + "\") has been assigned to " + escapeHtml(ticket.getAssignedToUserFullName()) + ".</p>";
        for (String to : recipientsForAssigneeAndCreator(ticket)) {
            send(to, subject, html);
        }
    }

    /** Fired from HelpDeskTicketService.resolve. */
    @Async
    public void notifyTicketResolved(HelpDeskTicket ticket) {
        String subject = "Help desk ticket resolved: " + ticket.getTicketNumber();
        String html = "<p>Ticket " + escapeHtml(ticket.getTicketNumber()) + " (\"" + escapeHtml(ticket.getSubject())
                + "\") has been marked resolved.</p>"
                + (ticket.getResolutionNotes() != null && !ticket.getResolutionNotes().isBlank()
                        ? "<p><strong>Resolution notes:</strong><br/>" + escapeHtml(ticket.getResolutionNotes()) + "</p>" : "");
        for (String to : recipientsForAssigneeAndCreator(ticket)) {
            send(to, subject, html);
        }
    }

    /** Dedupes the assignee and the creator in case they're the same person, and drops whichever has no resolvable address. */
    private Set<String> recipientsForAssigneeAndCreator(HelpDeskTicket ticket) {
        Set<String> recipients = new LinkedHashSet<>();
        String assigneeEmail = ticket.getAssignedToUserId() != null ? userEmail(ticket.getAssignedToUserId()) : null;
        if (assigneeEmail != null) {
            recipients.add(assigneeEmail);
        }
        String creatorEmail = resolveCreatorEmail(ticket);
        if (creatorEmail != null) {
            recipients.add(creatorEmail);
        }
        return recipients;
    }

    private String resolveCreatorEmail(HelpDeskTicket ticket) {
        if (ticket.getRequesterEmail() != null && !ticket.getRequesterEmail().isBlank()) {
            return ticket.getRequesterEmail().trim();
        }
        return ticket.getCreatedByUserId() != null ? userEmail(ticket.getCreatedByUserId()) : null;
    }

    private String userEmail(Long userId) {
        return userRepository.findById(userId)
                .map(AppUser::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .map(String::trim)
                .orElse(null);
    }

    private void send(String to, String subject, String html) {
        try {
            if (to == null || to.isBlank() || !mailService.isConfigured()) {
                return;
            }
            mailService.send(to.trim(), subject, html, List.of());
        } catch (Exception ex) {
            log.warn("Failed to send help desk email ('{}'): {}", subject, ex.getMessage());
        }
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
