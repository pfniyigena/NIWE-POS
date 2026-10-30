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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Help Desk tickets: the lifecycle (open, in progress, waiting for the customer, resolved, closed), replies and notes, and
 * the public side where a requester looks a ticket up with the ticket number and e-mail, answers, adds a file and rates the help.
 * Everything that changes a ticket also writes a line to its history ({@link HelpDeskEvents}).
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
    private final HelpDeskSettingsService settingsService;
    private final HelpDeskSlaService sla;
    private final HelpDeskEvents events;
    private final HelpDeskAttachmentService attachmentService;

    public HelpDeskTicketService(HelpDeskTicketRepository repository, HelpDeskCategoryRepository categoryRepository,
                                  HelpDeskCommentRepository commentRepository, CustomerRepository customerRepository,
                                  UserRepository userRepository, AuditLogService auditLogService,
                                  HelpDeskMailService mailService, StoreSettingsService storeSettingsService,
                                  DocumentPdfService documentPdfService, HelpDeskSettingsService settingsService,
                                  HelpDeskSlaService sla, HelpDeskEvents events, HelpDeskAttachmentService attachmentService) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.commentRepository = commentRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
        this.documentPdfService = documentPdfService;
        this.settingsService = settingsService;
        this.sla = sla;
        this.events = events;
        this.attachmentService = attachmentService;
    }

    HelpDeskTicketDto dto(HelpDeskTicket t) {
        return HelpDeskTicketDto.from(t, sla.warnHours());
    }

    /**
     * The admin ticket list. {@code view}: active (not resolved or closed), mine, unassigned, overdue (a deadline has passed),
     * due-soon, waiting, unanswered (no first reply yet) or blank for everything. {@code tag} keeps tickets carrying that tag.
     */
    @Transactional(readOnly = true)
    public List<HelpDeskTicketDto> search(HelpDeskTicketType type, HelpDeskStatus status, HelpDeskPriority priority,
                                            Long categoryId, Long assignedToUserId, String q, String view, String tag) {
        String normalizedQ = (q == null || q.isBlank()) ? "" : q.trim();
        Instant now = Instant.now();
        int warn = sla.warnHours();
        AppUser me = currentActor();
        String v = view == null ? "" : view.trim().toLowerCase(Locale.ROOT);
        String wantedTag = tag == null || tag.isBlank() ? null : tag.trim().toLowerCase(Locale.ROOT);
        return repository.search(type, status, priority, categoryId, assignedToUserId, normalizedQ).stream()
                .filter(t -> switch (v) {
                    case "active" -> !HelpDeskSla.terminal(t.getStatus());
                    case "mine" -> me != null && me.getId().equals(t.getAssignedToUserId()) && !HelpDeskSla.terminal(t.getStatus());
                    case "unassigned" -> t.getAssignedToUserId() == null && !HelpDeskSla.terminal(t.getStatus());
                    case "overdue" -> HelpDeskSla.overdue(t, now);
                    case "due-soon" -> HelpDeskSla.dueSoon(t, now, warn);
                    case "waiting" -> t.getStatus() == HelpDeskStatus.WAITING;
                    case "unanswered" -> (t.getStatus() == HelpDeskStatus.OPEN || t.getStatus() == HelpDeskStatus.IN_PROGRESS) && t.getFirstRespondedAt() == null;
                    default -> true;
                })
                .filter(t -> wantedTag == null || HelpDeskTicketDto.tagList(t.getTags()).contains(wantedTag))
                .map(t -> HelpDeskTicketDto.from(t, warn))
                .toList();
    }

    @Transactional(readOnly = true)
    public HelpDeskTicketDto getById(Long id) {
        return dto(getEntityOrThrow(id));
    }

    /** Print a ticket as PDF; every comment goes on the printout, internal notes included. */
    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        List<HelpDeskCommentDto> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(id)
                .stream().map(HelpDeskCommentDto::from).toList();
        return documentPdfService.helpdeskTicketPdf(dto(ticket), comments, storeSettingsService.getSettingsEntity());
    }

    /** Backs the Help Desk Tickets report; date range only. */
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
        sla.applyTargets(saved);
        auditLogService.record("HELPDESK_TICKET_CREATED",
                "Created " + saved.getType() + " ticket " + saved.getTicketNumber() + ": " + saved.getSubject(),
                "HelpDeskTicket", saved.getId());
        events.record(saved, HelpDeskEventKind.CREATED, "Created " + (saved.getType() == HelpDeskTicketType.CUSTOMER ? "customer" : "internal") + " ticket, priority " + saved.getPriority());
        if (saved.getAssignedToUserId() != null) {
            events.record(saved, HelpDeskEventKind.ASSIGNED, "Assigned to " + saved.getAssignedToUserFullName());
        }
        mailService.notifyTicketCreated(saved);
        if (saved.getAssignedToUserId() != null) {
            mailService.notifyTicketAssigned(saved);
        }
        return dto(saved);
    }

    /** The public, no-login half: always a CUSTOMER ticket with medium priority. */
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

        // Best-effort link to an existing Customer record by e-mail, so a returning customer's ticket shows against their record.
        customerRepository.findFirstByEmailIgnoreCase(request.getRequesterEmail()).ifPresent(customer -> {
            ticket.setCustomerId(customer.getId());
            ticket.setCustomerName(customer.getName());
        });

        HelpDeskTicket saved = repository.save(ticket);
        sla.applyTargets(saved);
        auditLogService.record("HELPDESK_TICKET_CREATED",
                "Guest-submitted ticket " + saved.getTicketNumber() + " from " + saved.getRequesterName() + ": " + saved.getSubject(),
                "HelpDeskTicket", saved.getId());
        events.record(saved, HelpDeskEventKind.CREATED, "Submitted on the Help Desk page", saved.getRequesterName());
        mailService.notifyTicketCreated(saved);
        return PublicHelpDeskTicketDto.from(saved);
    }

    /** Subject, description, category, priority, requester and customer — not the type. */
    @Transactional
    public HelpDeskTicketDto update(Long id, HelpDeskTicketRequest request) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        requireNotMerged(ticket);
        HelpDeskPriority before = ticket.getPriority();
        applyRequesterAndCategory(ticket, request);
        boolean priorityChanged = before != ticket.getPriority();
        if (priorityChanged) {
            sla.priorityChanged(ticket);
        }
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_UPDATED", "Updated ticket " + saved.getTicketNumber(), "HelpDeskTicket", saved.getId());
        events.record(saved, HelpDeskEventKind.EDITED, "Ticket details edited");
        if (priorityChanged) {
            events.record(saved, HelpDeskEventKind.PRIORITY_CHANGED, "Priority " + before + " -> " + saved.getPriority()
                    + (saved.getResolutionDueAt() != null ? " (deadlines recalculated)" : ""));
        }
        return dto(saved);
    }

    /** null unassigns. Allowed in any status; the assignee and the requester are e-mailed only when a new person takes it. */
    @Transactional
    public HelpDeskTicketDto assign(Long id, Long assignedToUserId) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        requireNotMerged(ticket);
        Long previousAssignee = ticket.getAssignedToUserId();
        applyAssignee(ticket, assignedToUserId);
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_ASSIGNED",
                saved.getAssignedToUserFullName() != null
                        ? "Assigned ticket " + saved.getTicketNumber() + " to " + saved.getAssignedToUserFullName()
                        : "Unassigned ticket " + saved.getTicketNumber(),
                "HelpDeskTicket", saved.getId());
        events.record(saved, HelpDeskEventKind.ASSIGNED, saved.getAssignedToUserFullName() != null ? "Assigned to " + saved.getAssignedToUserFullName() : "Unassigned");
        if (saved.getAssignedToUserId() != null && !saved.getAssignedToUserId().equals(previousAssignee)) {
            mailService.notifyTicketAssigned(saved);
        }
        return dto(saved);
    }

    /** From open (or waiting) to in progress. */
    @Transactional
    public HelpDeskTicketDto start(Long id) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        if (ticket.getStatus() != HelpDeskStatus.OPEN && ticket.getStatus() != HelpDeskStatus.WAITING) {
            throw ApiException.badRequest("Ticket is " + ticket.getStatus() + ", not OPEN or WAITING — can't start it");
        }
        boolean wasWaiting = ticket.getStatus() == HelpDeskStatus.WAITING;
        sla.endWaiting(ticket, Instant.now());
        ticket.setStatus(HelpDeskStatus.IN_PROGRESS);
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_STARTED", "Started work on ticket " + saved.getTicketNumber(), "HelpDeskTicket", saved.getId());
        events.record(saved, wasWaiting ? HelpDeskEventKind.RESUMED : HelpDeskEventKind.STARTED, wasWaiting ? "Back to work - the SLA clock runs again" : "Work started");
        return dto(saved);
    }

    /** Waiting for the customer: the SLA clock stops until work resumes or the customer answers. */
    @Transactional
    public HelpDeskTicketDto waitForCustomer(Long id) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        if (ticket.getStatus() != HelpDeskStatus.OPEN && ticket.getStatus() != HelpDeskStatus.IN_PROGRESS) {
            throw ApiException.badRequest("Ticket is " + ticket.getStatus() + " — only an open or in-progress ticket can wait for the customer");
        }
        HelpDeskTicketDto result = dto(setWaiting(ticket));
        return result;
    }

    private HelpDeskTicket setWaiting(HelpDeskTicket ticket) {
        ticket.setStatus(HelpDeskStatus.WAITING);
        sla.beginWaiting(ticket, Instant.now());
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_WAITING", "Ticket " + saved.getTicketNumber() + " is waiting for the customer", "HelpDeskTicket", saved.getId());
        events.record(saved, HelpDeskEventKind.WAITING, "Waiting for the customer - the SLA clock is paused");
        return saved;
    }

    @Transactional
    public HelpDeskTicketDto resolve(Long id, String resolutionNotes) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        if (HelpDeskSla.terminal(ticket.getStatus())) {
            throw ApiException.badRequest("Ticket is already " + ticket.getStatus());
        }
        Instant now = Instant.now();
        sla.endWaiting(ticket, now);
        ticket.setStatus(HelpDeskStatus.RESOLVED);
        ticket.setResolutionNotes(resolutionNotes);
        ticket.setResolvedAt(now);
        if (ticket.getFirstRespondedAt() == null) {
            ticket.setFirstRespondedAt(now);   // solving it counts as answering it
        }
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_RESOLVED", "Resolved ticket " + saved.getTicketNumber(), "HelpDeskTicket", saved.getId());
        events.record(saved, HelpDeskEventKind.RESOLVED, resolutionNotes == null || resolutionNotes.isBlank() ? "Resolved" : "Resolved: " + resolutionNotes);
        mailService.notifyTicketResolved(saved);
        return dto(saved);
    }

    @Transactional
    public HelpDeskTicketDto close(Long id) {
        HelpDeskTicket ticket = getEntityOrThrow(id);
        if (ticket.getStatus() == HelpDeskStatus.CLOSED) {
            throw ApiException.badRequest("Ticket is already closed");
        }
        requireNotMerged(ticket);
        Instant now = Instant.now();
        sla.endWaiting(ticket, now);
        ticket.setStatus(HelpDeskStatus.CLOSED);
        ticket.setClosedAt(now);
        HelpDeskTicket saved = repository.save(ticket);
        auditLogService.record("HELPDESK_TICKET_CLOSED", "Closed ticket " + saved.getTicketNumber(), "HelpDeskTicket", saved.getId());
        events.record(saved, HelpDeskEventKind.CLOSED, "Closed");
        return dto(saved);
    }

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
        events.record(saved, HelpDeskEventKind.REOPENED, "Reopened");
        return dto(saved);
    }

    @Transactional(readOnly = true)
    public List<HelpDeskCommentDto> listComments(Long ticketId) {
        getEntityOrThrow(ticketId);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream().map(HelpDeskCommentDto::from).toList();
    }

    /**
     * A note (internal) or a reply the requester can see. A visible reply is e-mailed to the requester when "e-mail replies" is on and
     * there is an address, counts as the first response, and can put the ticket on hold for the customer.
     */
    @Transactional
    public HelpDeskCommentDto addComment(Long ticketId, AddHelpDeskCommentRequest request) {
        HelpDeskTicket ticket = getEntityOrThrow(ticketId);
        requireNotMerged(ticket);
        AppUser actor = currentActor();
        Instant now = Instant.now();

        HelpDeskComment comment = new HelpDeskComment();
        comment.setTicket(ticket);
        comment.setAuthorUserId(actor != null ? actor.getId() : null);
        comment.setAuthorUserFullName(actor != null ? actor.getFullName() : "system");
        comment.setBody(request.getBody().trim());
        comment.setInternal(request.isInternal());

        boolean emailed = false;
        if (!request.isInternal()) {
            if (ticket.getFirstRespondedAt() == null) {
                ticket.setFirstRespondedAt(now);
            }
            HelpDeskSettings s = settingsService.entity();
            emailed = s.isReplyEmailEnabled() && ticket.getRequesterEmail() != null && !ticket.getRequesterEmail().isBlank();
            comment.setEmailed(emailed);
        }
        HelpDeskComment saved = commentRepository.save(comment);
        repository.save(ticket);

        auditLogService.record("HELPDESK_COMMENT_ADDED",
                (request.isInternal() ? "Added an internal note to ticket " : "Added a visible update to ticket ")
                        + ticket.getTicketNumber(),
                "HelpDeskTicket", ticket.getId());
        if (request.isInternal()) {
            events.record(ticket, HelpDeskEventKind.NOTE, "Internal note added");
        } else {
            events.record(ticket, HelpDeskEventKind.REPLY_STAFF, emailed ? "Reply sent to the requester (e-mailed)" : "Reply added for the requester");
            if (emailed) {
                mailService.notifyStaffReply(ticket, saved.getBody(), saved.getAuthorUserFullName());
            }
            if (request.isWaitForCustomer() && (ticket.getStatus() == HelpDeskStatus.OPEN || ticket.getStatus() == HelpDeskStatus.IN_PROGRESS)) {
                setWaiting(ticket);
            }
        }
        return HelpDeskCommentDto.from(saved);
    }

    // ---- the public side ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PublicHelpDeskTicketStatusDto publicLookup(String ticketNumber, String email) {
        HelpDeskTicket ticket = findPublic(ticketNumber, email);
        HelpDeskSettings s = settingsService.entity();
        List<PublicHelpDeskCommentDto> updates = commentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(ticket.getId())
                .stream().map(PublicHelpDeskCommentDto::from).toList();
        List<PublicHelpDeskTicketStatusDto.PublicAttachmentDto> files = attachmentService.visibleTo(ticket.getId()).stream()
                .map(a -> new PublicHelpDeskTicketStatusDto.PublicAttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.isFromRequester(), a.getCreatedAt()))
                .toList();
        HelpDeskCategory category = ticket.getCategory();
        boolean closedForReplies = ticket.getStatus() == HelpDeskStatus.CLOSED || ticket.getStatus() == HelpDeskStatus.MERGED;
        boolean canRate = s.isSatisfactionEnabled() && ticket.getSatisfactionRating() == null
                && (ticket.getStatus() == HelpDeskStatus.RESOLVED || ticket.getStatus() == HelpDeskStatus.CLOSED);
        return new PublicHelpDeskTicketStatusDto(
                ticket.getTicketNumber(), ticket.getSubject(), ticket.getStatus(),
                category != null ? category.getName() : null,
                ticket.getCreatedAt(), ticket.getResolvedAt(), ticket.getClosedAt(),
                ticket.getResolutionNotes(), updates,
                s.isPublicReplyEnabled() && !closedForReplies, canRate,
                ticket.getSatisfactionRating(), ticket.getSatisfactionComment(), files
        );
    }

    /** The requester answers: it reopens a resolved ticket, puts a waiting one back to work, and tells the assignee. */
    @Transactional
    public PublicHelpDeskTicketStatusDto publicReply(String ticketNumber, String email, String body) {
        HelpDeskTicket ticket = findPublic(ticketNumber, email);
        HelpDeskSettings s = settingsService.entity();
        if (!s.isPublicReplyEnabled()) throw ApiException.forbidden("Replying on the website is switched off. Please contact us another way.");
        if (body == null || body.isBlank()) throw ApiException.badRequest("Write your reply first.");
        if (body.length() > 5000) throw ApiException.badRequest("A reply can be at most 5000 characters.");
        if (ticket.getStatus() == HelpDeskStatus.MERGED) throw ApiException.badRequest("This ticket was merged into " + ticket.getMergedIntoNumber() + ". Reply on that ticket.");
        if (ticket.getStatus() == HelpDeskStatus.CLOSED) throw ApiException.badRequest("This ticket is closed. Please submit a new ticket.");
        if (commentRepository.countByTicketIdAndFromRequesterTrue(ticket.getId()) >= 100) throw ApiException.badRequest("This ticket has too many replies. Please contact us another way.");
        Instant now = Instant.now();
        String name = ticket.getRequesterName() == null || ticket.getRequesterName().isBlank() ? "Requester" : ticket.getRequesterName();

        HelpDeskComment c = new HelpDeskComment();
        c.setTicket(ticket);
        c.setAuthorUserId(null);
        c.setAuthorUserFullName(name);
        c.setBody(body.trim());
        c.setInternal(false);
        c.setFromRequester(true);
        commentRepository.save(c);

        ticket.setLastRequesterReplyAt(now);
        if (ticket.getStatus() == HelpDeskStatus.RESOLVED) {
            ticket.setStatus(HelpDeskStatus.OPEN);
            ticket.setResolvedAt(null);
            events.record(ticket, HelpDeskEventKind.REOPENED, "Reopened by the requester's reply", name);
        } else if (ticket.getStatus() == HelpDeskStatus.WAITING) {
            sla.endWaiting(ticket, now);
            ticket.setStatus(ticket.getAssignedToUserId() != null ? HelpDeskStatus.IN_PROGRESS : HelpDeskStatus.OPEN);
            events.record(ticket, HelpDeskEventKind.RESUMED, "The requester answered - the SLA clock runs again", name);
        }
        repository.save(ticket);
        events.record(ticket, HelpDeskEventKind.REPLY_REQUESTER, "Requester replied", name);
        auditLogService.record("HELPDESK_COMMENT_ADDED", "Requester replied to ticket " + ticket.getTicketNumber(), "HelpDeskTicket", ticket.getId());
        mailService.notifyRequesterReply(ticket, c.getBody());
        return publicLookup(ticketNumber, email);
    }

    @Transactional
    public PublicHelpDeskTicketStatusDto publicRate(String ticketNumber, String email, int rating, String comment) {
        HelpDeskTicket ticket = findPublic(ticketNumber, email);
        if (!settingsService.entity().isSatisfactionEnabled()) throw ApiException.forbidden("Ratings are switched off.");
        if (ticket.getStatus() != HelpDeskStatus.RESOLVED && ticket.getStatus() != HelpDeskStatus.CLOSED) throw ApiException.badRequest("You can rate a ticket once it is resolved.");
        if (ticket.getSatisfactionRating() != null) throw ApiException.badRequest("This ticket has already been rated.");
        if (rating < 1 || rating > 5) throw ApiException.badRequest("Choose a rating from 1 to 5.");
        if (comment != null && comment.length() > 1000) throw ApiException.badRequest("The comment can be at most 1000 characters.");
        ticket.setSatisfactionRating(rating);
        ticket.setSatisfactionComment(comment == null || comment.isBlank() ? null : comment.trim());
        ticket.setRatedAt(Instant.now());
        repository.save(ticket);
        events.record(ticket, HelpDeskEventKind.RATED, "Rated " + rating + " out of 5" + (ticket.getSatisfactionComment() != null ? ": " + ticket.getSatisfactionComment() : ""),
                ticket.getRequesterName() == null ? "Requester" : ticket.getRequesterName());
        return publicLookup(ticketNumber, email);
    }

    @Transactional
    public void publicAddFile(String ticketNumber, String email, MultipartFile file) throws IOException {
        HelpDeskTicket ticket = findPublic(ticketNumber, email);
        if (!settingsService.entity().isPublicReplyEnabled()) throw ApiException.forbidden("Adding files on the website is switched off.");
        if (ticket.getStatus() == HelpDeskStatus.CLOSED || ticket.getStatus() == HelpDeskStatus.MERGED) throw ApiException.badRequest("This ticket is closed. Please submit a new ticket.");
        attachmentService.addByRequester(ticket, file);
    }

    @Transactional(readOnly = true)
    public HelpDeskAttachmentService.FileData publicFile(String ticketNumber, String email, Long attachmentId) {
        return attachmentService.publicFile(findPublic(ticketNumber, email), attachmentId);
    }

    HelpDeskTicket findPublic(String ticketNumber, String email) {
        if (ticketNumber == null || email == null) throw ApiException.notFound("No ticket matches that ticket number and email");
        return repository.findByTicketNumberIgnoreCaseAndRequesterEmailIgnoreCase(ticketNumber.trim(), email.trim())
                .orElseThrow(() -> ApiException.notFound("No ticket matches that ticket number and email"));
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

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

    private void requireNotMerged(HelpDeskTicket t) {
        if (t.getStatus() == HelpDeskStatus.MERGED) {
            throw ApiException.badRequest("This ticket was merged into " + t.getMergedIntoNumber() + ". Work on that ticket instead.");
        }
    }

    private String generateTicketNumber() {
        String n;
        do {
            n = "HD-" + System.currentTimeMillis();
        } while (repository.existsByTicketNumber(n));
        return n;
    }

    HelpDeskTicket getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Ticket not found: " + id));
    }

    /** null (handled by every caller) rather than throwing when nothing is authenticated. */
    AppUser currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            return null;
        }
        return principal.getUser();
    }
}
