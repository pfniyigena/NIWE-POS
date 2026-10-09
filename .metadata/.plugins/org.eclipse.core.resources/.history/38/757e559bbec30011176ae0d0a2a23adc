package com.posapp.helpdesk;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.DocumentPdfService;
import com.posapp.helpdesk.dto.AddHelpDeskCommentRequest;
import com.posapp.helpdesk.dto.HelpDeskCommentDto;
import com.posapp.helpdesk.dto.HelpDeskTicketDto;
import com.posapp.helpdesk.dto.HelpDeskTicketReportRow;
import com.posapp.helpdesk.dto.HelpDeskTicketRequest;
import com.posapp.helpdesk.dto.PublicHelpDeskCommentDto;
import com.posapp.helpdesk.dto.PublicHelpDeskTicketDto;
import com.posapp.helpdesk.dto.PublicHelpDeskTicketRequest;
import com.posapp.helpdesk.dto.PublicHelpDeskTicketStatusDto;
import com.posapp.security.AppUserPrincipal;
import com.posapp.settings.StoreSettingsService;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Fourier: "Add Help Desk module (with its menu) should manage customer and internal support
 * requests from creation through resolution." Scoped via AskUserQuestion: one unified ticket model
 * for both {@link HelpDeskTicketType#CUSTOMER} and {@link HelpDeskTicketType#INTERNAL} requests
 * (see {@link HelpDeskTicket}'s own javadoc), configurable {@link HelpDeskCategory} for routing,
 * and plain {@link HelpDeskPriority} levels with no SLA/due-date tracking. Status moves through
 * explicit named actions ({@link #start}/{@link #resolve}/{@link #close}/{@link #reopen}), the
 * same convention {@code SubscriptionService}'s pause/resume/cancel already uses, rather than a
 * generic "set status" endpoint.
 *
 * <p>Two ways in: {@link #createByStaff} (a logged-in ADMIN, from the admin Help Desk screen,
 * usually linked to an existing {@code Customer}) and {@link #createByGuest} (no login at all, via
 * {@code PublicHelpDeskController} — Fourier: "Customer login is not necessary to create a Help
 * Desk ticket ... supporting both authenticated and guest ticket creation"). A guest ticket tries
 * to match an existing {@code Customer} by email ({@link #createByGuest}) so it still shows up
 * against that customer's own record when one exists, without requiring the guest to have ever
 * logged in anywhere — this app has no customer-facing login system at all.
 */
@Service
public class HelpDeskTicketService {

    private final HelpDeskTicketRepository repository;
    private final HelpDeskCategoryRepository categoryRepository;
    private final HelpDeskCommentRepository commentRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final HelpDeskMailService mailService;
    private final StoreSettingsService storeSettingsService;
    private final DocumentPdfService documentPdfService;

    public HelpDeskTicketService(HelpDeskTicketRepository repository, HelpDeskCategoryRepository categoryRepository,
                                  HelpDeskCommentRepository commentRepository, CustomerRepository customerRepository,
                                  UserRepository userRepository, AuditLogService auditLogService,
                                  HelpDeskMailService mailService, StoreSettingsService storeSettingsService,
                                  DocumentPdfService documentPdfService) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.commentRepository = commentRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
        this.documentPdfService = documentPdfService;
    }

    /** The admin ticket list — every filter optional/nullable, see HelpDeskTicketRepository.search's own javadoc. */
    @Transactional(readOnly = true)
    public List<HelpDeskTicketDto> search(HelpDeskTicketType type, HelpDeskStatus status, HelpDeskPriority priority,
                                            Long categoryId, Long assignedToUserId, String q) {
        String normalizedQ = (q == null || q.isBlank()) ? "" : q.trim();
        return repository.search(type, status, priority, categoryId, assignedToUserId, normalizedQ)
                .stream().map(HelpDeskTicketDto::from).toList();
    }

    @Transactional(readOnly = true)
    public HelpDeskTicketDto getById(Long id) {
        return HelpDeskTicketDto.from(getEntityOrThrow(id));
    }

    /**
     * Fourier: "print tick[et] on PDF" — the admin ticket detail screen's own "Print" button, same
     * {@code DocumentPdfService}-backed convention as InvoiceService.pdf/SalesOrderService.pdf.
     * Every comment goes on the printout (see {@code DocumentPdfService.helpdeskTicketPdf}'s own
     * javadoc for why internal notes aren't excluded here the way they are from the public lookup).
     */
    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        List<HelpDeskCommentDto> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(id)
                .stream().map(HelpDeskCommentDto::from).toList();
        return documentPdfService.helpdeskTicketPdf(HelpDeskTicketDto.from(ticket), comments, storeSettingsService.getSettingsEntity());
    }

    /** Fourier: "Add Helpdesk reports(XLS,PDF)" — backs HelpDeskReportController's one report; see HelpDeskTicketReportRow's own javadoc for the columns. Date range only, same "dateOnly" shape as the simpler reports in ReportController (no register — a ticket isn't tied to one). */
    @Transactional(readOnly = true)
    public List<HelpDeskTicketReportRow> reportRows(Instant from, Instant to) {
        return repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)
                .stream().map(HelpDeskTicketReportRow::from).toList();
    }

    @Transactional
    public HelpDeskTicketDto createByStaff(HelpDeskTicketRequest request) {
        HelpDeskTicket ticket = new HelpDeskTicket();
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setType(request.getType());
        applyRequesterAndCategory(ticket, request);
        ticket.setStatus(HelpDeskStatus.OPEN);

        AppUser actor = currentActor();
        ticket.setCreatedByUserId(actor != null ? actor.getId() : null);
        ticket.setCreatedByUserFullName(actor != null ? actor.getFullName() : null);

        if (request.getAssignedToUserId() != null) {
            applyAssignee(ticket, request.getAssignedToUserId());
        }

        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_CREATED",
                "Created " + saved.getType() + " ticket " + saved.getTicketNumber() + ": " + saved.getSubject(),
                "HelpDeskTicket", saved.getId());
        mailService.notifyTicketCreated(saved);
        // Assigning at creation time is still "assigned to a staff" — same email as a later assign() call.
        if (saved.getAssignedToUserId() != null) {
            mailService.notifyTicketAssigned(saved);
        }
        return HelpDeskTicketDto.from(saved);
    }

    /**
     * Fourier: "supporting both authenticated and guest ticket creation" — the public, no-login
     * half. Always {@link HelpDeskTicketType#CUSTOMER}; see {@code PublicHelpDeskTicketRequest}'s
     * own javadoc for why there's no public way to raise an INTERNAL ticket or set a priority.
     */
    @Transactional
    public PublicHelpDeskTicketDto createByGuest(PublicHelpDeskTicketRequest request) {
        HelpDeskTicket ticket = new HelpDeskTicket();
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setType(HelpDeskTicketType.CUSTOMER);
        ticket.setSubject(request.getSubject());
        ticket.setDescription(request.getDescription());
        ticket.setPriority(HelpDeskPriority.MEDIUM);
        ticket.setStatus(HelpDeskStatus.OPEN);
        ticket.setRequesterName(request.getRequesterName());
        ticket.setRequesterEmail(request.getRequesterEmail());
        ticket.setRequesterPhone(request.getRequesterPhone());

        if (request.getCategoryId() != null) {
            HelpDeskCategory category = categoryRepository.findById(request.getCategoryId())
                    .filter(HelpDeskCategory::isActive)
                    .orElseThrow(() -> ApiException.badRequest("Select a valid category"));
            ticket.setCategory(category);
        }

        // Fourier: "supporting both authenticated and guest ticket creation" — best-effort link to
        // an existing Customer record by email, so a returning customer's guest-submitted ticket
        // still shows up against their own record. See CustomerRepository.findFirstByEmailIgnoreCase's
        // own javadoc for why this is "first match wins," not a strict uniqueness guarantee.
        customerRepository.findFirstByEmailIgnoreCase(request.getRequesterEmail()).ifPresent(customer -> {
            ticket.setCustomerId(customer.getId());
            ticket.setCustomerName(customer.getName());
        });

        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_CREATED",
                "Guest-submitted ticket " + saved.getTicketNumber() + " from " + saved.getRequesterName() + ": " + saved.getSubject(),
                "HelpDeskTicket", saved.getId());
        mailService.notifyTicketCreated(saved);
        return PublicHelpDeskTicketDto.from(saved);
    }

    /** Subject/description/category/priority/requester/customer — not type, see HelpDeskTicketRequest's own javadoc. */
    @Transactional
    public HelpDeskTicketDto update(Long id, HelpDeskTicketRequest request) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        applyRequesterAndCategory(ticket, request);
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_UPDATED", "Updated ticket " + saved.getTicketNumber(), "HelpDeskTicket", saved.getId());
        return HelpDeskTicketDto.from(saved);
    }

    /**
     * null unassigns. Allowed regardless of status — reassignment can happen even after a ticket
     * is resolved/closed, for follow-up. Fourier: "Once the ticket is assigned to a staff, staff
     * and the ticket creator should receive emails" — only fired when this call actually lands on
     * a new, non-null assignee (not on an unassign, and not a no-op reassignment to the same
     * person), so reassigning away from someone, or re-saving the same assignee, doesn't spam
     * either party with a duplicate "assigned" email.
     */
    @Transactional
    public HelpDeskTicketDto assign(Long id, Long assignedToUserId) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        Long previousAssignee = ticket.getAssignedToUserId();
        applyAssignee(ticket, assignedToUserId);
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_ASSIGNED",
                saved.getAssignedToUserFullName() != null
                        ? "Assigned ticket " + saved.getTicketNumber() + " to " + saved.getAssignedToUserFullName()
                        : "Unassigned ticket " + saved.getTicketNumber(),
                "HelpDeskTicket", saved.getId());
        if (saved.getAssignedToUserId() != null && !saved.getAssignedToUserId().equals(previousAssignee)) {
            mailService.notifyTicketAssigned(saved);
        }
        return HelpDeskTicketDto.from(saved);
    }

    @Transactional
    public HelpDeskTicketDto start(Long id) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        if (ticket.getStatus() != HelpDeskStatus.OPEN) {
            throw ApiException.badRequest("Ticket is " + ticket.getStatus() + ", not OPEN — can't start it");
        }
        ticket.setStatus(HelpDeskStatus.IN_PROGRESS);
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_STARTED", "Started work on ticket " + saved.getTicketNumber(), "HelpDeskTicket", saved.getId());
        return HelpDeskTicketDto.from(saved);
    }

    @Transactional
    public HelpDeskTicketDto resolve(Long id, String resolutionNotes) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        if (ticket.getStatus() == HelpDeskStatus.RESOLVED || ticket.getStatus() == HelpDeskStatus.CLOSED) {
            throw ApiException.badRequest("Ticket is already " + ticket.getStatus());
        }
        ticket.setStatus(HelpDeskStatus.RESOLVED);
        ticket.setResolutionNotes(resolutionNotes);
        ticket.setResolvedAt(Instant.now());
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_RESOLVED", "Resolved ticket " + saved.getTicketNumber(), "HelpDeskTicket", saved.getId());
        mailService.notifyTicketResolved(saved);
        return HelpDeskTicketDto.from(saved);
    }

    /** Reachable from any status except CLOSED — a ticket closed without a formal "resolve" step (e.g. duplicate, spam) is a normal path, not an error. */
    @Transactional
    public HelpDeskTicketDto close(Long id) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        if (ticket.getStatus() == HelpDeskStatus.CLOSED) {
            throw ApiException.badRequest("Ticket is already closed");
        }
        ticket.setStatus(HelpDeskStatus.CLOSED);
        ticket.setClosedAt(Instant.now());
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_CLOSED", "Closed ticket " + saved.getTicketNumber(), "HelpDeskTicket", saved.getId());
        return HelpDeskTicketDto.from(saved);
    }

    /** From RESOLVED or CLOSED only — e.g. the requester reports the issue isn't actually fixed. Clears resolvedAt/closedAt; resolutionNotes is kept as history. */
    @Transactional
    public HelpDeskTicketDto reopen(Long id) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        if (ticket.getStatus() != HelpDeskStatus.RESOLVED && ticket.getStatus() != HelpDeskStatus.CLOSED) {
            throw ApiException.badRequest("Ticket is " + ticket.getStatus() + " — only a resolved or closed ticket can be reopened");
        }
        ticket.setStatus(HelpDeskStatus.OPEN);
        ticket.setResolvedAt(null);
        ticket.setClosedAt(null);
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_REOPENED", "Reopened ticket " + saved.getTicketNumber(), "HelpDeskTicket", saved.getId());
        return HelpDeskTicketDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<HelpDeskCommentDto> listComments(Long ticketId) {
        getEntityOrThrow(ticketId);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream().map(HelpDeskCommentDto::from).toList();
    }

    @Transactional
    public HelpDeskCommentDto addComment(Long ticketId, AddHelpDeskCommentRequest request) {
        HelpDeskTicket ticket = getEntityOrThrow(ticketId);
        AppUser actor = currentActor();

        HelpDeskComment comment = new HelpDeskComment();
        comment.setTicket(ticket);
        comment.setAuthorUserId(actor != null ? actor.getId() : null);
        comment.setAuthorUserFullName(actor != null ? actor.getFullName() : "system");
        comment.setBody(request.getBody());
        comment.setInternal(request.isInternal());
        HelpDeskComment saved = commentRepository.save(comment);

        auditLogService.record("HELPDESK_COMMENT_ADDED",
                (request.isInternal() ? "Added an internal note to ticket " : "Added a visible update to ticket ")
                        + ticket.getTicketNumber(),
                "HelpDeskTicket", ticket.getId());
        return HelpDeskCommentDto.from(saved);
    }

    /**
     * Fourier: "supporting ... guest ticket creation" naturally implies being able to check on one
     * afterward too — PublicHelpDeskController's own no-login lookup, by ticket number + the email
     * given at creation (see HelpDeskTicketRepository's own javadoc). Never reveals whether a
     * ticket number alone exists if the email doesn't match it — same not-found either way, so this
     * can't be used to enumerate ticket numbers.
     */
    @Transactional(readOnly = true)
    public PublicHelpDeskTicketStatusDto publicLookup(String ticketNumber, String email) {
        HelpDeskTicket ticket = repository.findByTicketNumberIgnoreCaseAndRequesterEmailIgnoreCase(ticketNumber, email)
                .orElseThrow(() -> ApiException.notFound("No ticket matches that ticket number and email"));
        List<PublicHelpDeskCommentDto> updates = commentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(ticket.getId())
                .stream().map(PublicHelpDeskCommentDto::from).toList();
        HelpDeskCategory category = ticket.getCategory();
        return new PublicHelpDeskTicketStatusDto(
                ticket.getTicketNumber(), ticket.getSubject(), ticket.getStatus(),
                category != null ? category.getName() : null,
                ticket.getCreatedAt(), ticket.getResolvedAt(), ticket.getClosedAt(),
                ticket.getResolutionNotes(), updates
        );
    }

    private void applyRequesterAndCategory(HelpDeskTicket ticket, HelpDeskTicketRequest request) {
        ticket.setSubject(request.getSubject());
        ticket.setDescription(request.getDescription());
        ticket.setPriority(request.getPriority() != null ? request.getPriority() : HelpDeskPriority.MEDIUM);

        if (request.getCategoryId() != null) {
            HelpDeskCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> ApiException.notFound("Category not found: " + request.getCategoryId()));
            ticket.setCategory(category);
        } else {
            ticket.setCategory(null);
        }

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> ApiException.notFound("Customer not found: " + request.getCustomerId()));
            ticket.setCustomerId(customer.getId());
            ticket.setCustomerName(customer.getName());
            // Pre-fill requester details from the customer record when the form didn't override them.
            ticket.setRequesterName(request.getRequesterName() != null && !request.getRequesterName().isBlank()
                    ? request.getRequesterName() : customer.getName());
            ticket.setRequesterEmail(request.getRequesterEmail() != null && !request.getRequesterEmail().isBlank()
                    ? request.getRequesterEmail() : customer.getEmail());
            ticket.setRequesterPhone(request.getRequesterPhone() != null && !request.getRequesterPhone().isBlank()
                    ? request.getRequesterPhone() : customer.getPhone());
        } else {
            ticket.setCustomerId(null);
            ticket.setCustomerName(null);
            ticket.setRequesterName(request.getRequesterName());
            ticket.setRequesterEmail(request.getRequesterEmail());
            ticket.setRequesterPhone(request.getRequesterPhone());
        }
    }

    private void applyAssignee(HelpDeskTicket ticket, Long assignedToUserId) {
        if (assignedToUserId == null) {
            ticket.setAssignedToUserId(null);
            ticket.setAssignedToUserFullName(null);
            return;
        }
        AppUser user = userRepository.findById(assignedToUserId)
                .orElseThrow(() -> ApiException.notFound("User not found: " + assignedToUserId));
        ticket.setAssignedToUserId(user.getId());
        ticket.setAssignedToUserFullName(user.getFullName());
    }

    private String generateTicketNumber() {
        return "HD-" + System.currentTimeMillis();
    }

    private HelpDeskTicket getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Ticket not found: " + id));
    }

    /** Same SecurityContextHolder pattern as AuditLogService.currentActor — null (handled by every caller above) rather than throwing when nothing is authenticated. */
    private AppUser currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            return null;
        }
        return principal.getUser();
    }
}
