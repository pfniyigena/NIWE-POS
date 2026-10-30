package com.posapp.helpdesk;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.helpdesk.dto.HelpDeskTicketDto;
import com.posapp.helpdesk.dto.HelpDeskWorkflowRequests.BulkRequest;
import com.posapp.helpdesk.dto.HelpDeskWorkflowRequests.BulkResult;
import com.posapp.helpdesk.dto.HelpDeskWorkflowRequests.Skipped;
import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.workorder.WorkOrder;
import com.posapp.workorder.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Tags, links to an invoice or a job, merging duplicates, and acting on many tickets at once. */
@Service
public class HelpDeskWorkflowService {

    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 30;
    private static final int MAX_BULK = 200;

    private final HelpDeskTicketRepository repository;
    private final HelpDeskCommentRepository commentRepository;
    private final HelpDeskAttachmentRepository attachmentRepository;
    private final HelpDeskTicketService ticketService;
    private final HelpDeskSlaService sla;
    private final HelpDeskEvents events;
    private final AuditLogService auditLogService;
    private final InvoiceRepository invoiceRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TransactionTemplate tx;

    public HelpDeskWorkflowService(HelpDeskTicketRepository repository, HelpDeskCommentRepository commentRepository,
                                   HelpDeskAttachmentRepository attachmentRepository, HelpDeskTicketService ticketService, HelpDeskSlaService sla,
                                   HelpDeskEvents events, AuditLogService auditLogService, InvoiceRepository invoiceRepository,
                                   WorkOrderRepository workOrderRepository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.commentRepository = commentRepository;
        this.attachmentRepository = attachmentRepository;
        this.ticketService = ticketService;
        this.sla = sla;
        this.events = events;
        this.auditLogService = auditLogService;
        this.invoiceRepository = invoiceRepository;
        this.workOrderRepository = workOrderRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public HelpDeskTicketDto setTags(Long id, List<String> raw) {
        HelpDeskTicket t = ticketService.getEntityOrThrow(id);
        Set<String> tags = normalize(raw);
        t.setTags(tags.isEmpty() ? null : String.join(",", tags));
        repository.save(t);
        events.record(t, HelpDeskEventKind.TAGS, tags.isEmpty() ? "Tags cleared" : "Tags: " + String.join(", ", tags));
        return ticketService.dto(t);
    }

    @Transactional
    public HelpDeskTicketDto setLinks(Long id, String invoiceNumber, String workOrderNumber) {
        HelpDeskTicket t = ticketService.getEntityOrThrow(id);
        List<String> parts = new ArrayList<>();
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            t.setLinkedInvoiceId(null);
            t.setLinkedInvoiceNumber(null);
        } else {
            Invoice inv = invoiceRepository.findFirstByInvoiceNumberIgnoreCase(invoiceNumber.trim())
                    .orElseThrow(() -> ApiException.badRequest("No invoice is numbered " + invoiceNumber.trim()));
            t.setLinkedInvoiceId(inv.getId());
            t.setLinkedInvoiceNumber(inv.getInvoiceNumber());
            parts.add("invoice " + inv.getInvoiceNumber());
        }
        if (workOrderNumber == null || workOrderNumber.isBlank()) {
            t.setLinkedWorkOrderId(null);
            t.setLinkedWorkOrderNumber(null);
        } else {
            WorkOrder wo = workOrderRepository.findFirstByNumberIgnoreCase(workOrderNumber.trim())
                    .orElseThrow(() -> ApiException.badRequest("No job is numbered " + workOrderNumber.trim()));
            t.setLinkedWorkOrderId(wo.getId());
            t.setLinkedWorkOrderNumber(wo.getNumber());
            parts.add("job " + wo.getNumber());
        }
        repository.save(t);
        events.record(t, HelpDeskEventKind.LINKED, parts.isEmpty() ? "Links removed" : "Linked to " + String.join(" and ", parts));
        return ticketService.dto(t);
    }

    /** Folds a duplicate into another ticket: its replies and files move across and it is closed as MERGED. */
    @Transactional
    public HelpDeskTicketDto merge(Long sourceId, Long targetId) {
        if (targetId == null) throw ApiException.badRequest("Choose the ticket to merge into.");
        if (sourceId.equals(targetId)) throw ApiException.badRequest("A ticket cannot be merged into itself.");
        HelpDeskTicket source = ticketService.getEntityOrThrow(sourceId);
        HelpDeskTicket target = ticketService.getEntityOrThrow(targetId);
        if (source.getStatus() == HelpDeskStatus.MERGED) throw ApiException.badRequest("This ticket was already merged into " + source.getMergedIntoNumber() + ".");
        if (target.getStatus() == HelpDeskStatus.MERGED) throw ApiException.badRequest("The ticket " + target.getTicketNumber() + " was itself merged into " + target.getMergedIntoNumber() + ". Merge into that one.");
        if (HelpDeskSla.terminal(target.getStatus())) throw ApiException.badRequest("The ticket " + target.getTicketNumber() + " is " + target.getStatus() + ". Reopen it first, or merge into an open ticket.");
        Instant now = Instant.now();

        for (HelpDeskComment c : commentRepository.findByTicketIdOrderByCreatedAtAsc(sourceId)) {
            c.setTicket(target);
            commentRepository.save(c);
        }
        for (HelpDeskAttachment a : attachmentRepository.findByTicketIdOrderByIdAsc(sourceId)) {
            a.setTicketId(targetId);
            attachmentRepository.save(a);
        }
        HelpDeskComment note = new HelpDeskComment();
        note.setTicket(target);
        note.setAuthorUserId(null);
        note.setAuthorUserFullName(HelpDeskEvents.actorName());
        note.setInternal(true);
        note.setBody("Merged ticket " + source.getTicketNumber() + " (" + source.getSubject() + ") into this one.\n\nIts description was:\n" + source.getDescription());
        commentRepository.save(note);
        if (source.getLastRequesterReplyAt() != null && (target.getLastRequesterReplyAt() == null || source.getLastRequesterReplyAt().isAfter(target.getLastRequesterReplyAt()))) {
            target.setLastRequesterReplyAt(source.getLastRequesterReplyAt());
        }
        repository.save(target);

        sla.endWaiting(source, now);
        source.setStatus(HelpDeskStatus.MERGED);
        source.setMergedIntoId(target.getId());
        source.setMergedIntoNumber(target.getTicketNumber());
        source.setClosedAt(now);
        repository.save(source);

        events.record(source, HelpDeskEventKind.MERGED, "Merged into " + target.getTicketNumber());
        events.record(target, HelpDeskEventKind.MERGED, "Ticket " + source.getTicketNumber() + " was merged into this one");
        auditLogService.record("HELPDESK_TICKET_MERGED", "Merged ticket " + source.getTicketNumber() + " into " + target.getTicketNumber(), "HelpDeskTicket", target.getId());
        return ticketService.dto(source);
    }

    /** Not transactional: each ticket is its own change, so one that cannot be changed does not undo the others. */
    public BulkResult bulk(BulkRequest r) {
        if (r.getIds() == null || r.getIds().isEmpty()) throw ApiException.badRequest("Choose at least one ticket.");
        if (r.getIds().size() > MAX_BULK) throw ApiException.badRequest("At most " + MAX_BULK + " tickets at a time.");
        String action = r.getAction() == null ? "" : r.getAction().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ASSIGN", "CLOSE", "PRIORITY", "TAG", "WAIT", "START").contains(action)) throw ApiException.badRequest("Unknown action: " + r.getAction());
        if (action.equals("PRIORITY") && r.getPriority() == null) throw ApiException.badRequest("Choose a priority.");
        if (action.equals("TAG") && normalize(List.of(r.getTag() == null ? "" : r.getTag())).isEmpty()) throw ApiException.badRequest("Enter a tag.");
        int done = 0;
        List<Skipped> skipped = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(r.getIds())) {
            HelpDeskTicket t = repository.findById(id).orElse(null);
            if (t == null) {
                skipped.add(new Skipped(id, null, "Not found"));
                continue;
            }
            try {
                tx.executeWithoutResult(status -> {
                    switch (action) {
                        case "ASSIGN" -> ticketService.assign(id, r.getAssignedToUserId());
                        case "CLOSE" -> ticketService.close(id);
                        case "WAIT" -> ticketService.waitForCustomer(id);
                        case "START" -> ticketService.start(id);
                        case "PRIORITY" -> changePriority(id, r.getPriority());
                        default -> addTag(id, r.getTag());
                    }
                });
                done++;
            } catch (ApiException ex) {
                skipped.add(new Skipped(id, t.getTicketNumber(), ex.getMessage()));
            }
        }
        return new BulkResult(done, skipped);
    }

    @Transactional
    public void changePriority(Long id, HelpDeskPriority priority) {
        HelpDeskTicket t = ticketService.getEntityOrThrow(id);
        if (t.getStatus() == HelpDeskStatus.MERGED) throw ApiException.badRequest("This ticket was merged into " + t.getMergedIntoNumber());
        HelpDeskPriority before = t.getPriority();
        if (before == priority) return;
        t.setPriority(priority);
        sla.priorityChanged(t);
        repository.save(t);
        events.record(t, HelpDeskEventKind.PRIORITY_CHANGED, "Priority " + before + " -> " + priority + (t.getResolutionDueAt() != null ? " (deadlines recalculated)" : ""));
    }

    @Transactional
    public void addTag(Long id, String tag) {
        HelpDeskTicket t = ticketService.getEntityOrThrow(id);
        Set<String> tags = normalize(HelpDeskTicketDto.tagList(t.getTags()));
        tags.addAll(normalize(List.of(tag)));
        if (tags.size() > MAX_TAGS) throw ApiException.badRequest("A ticket can have at most " + MAX_TAGS + " tags.");
        t.setTags(String.join(",", tags));
        repository.save(t);
        events.record(t, HelpDeskEventKind.TAGS, "Tags: " + String.join(", ", tags));
    }

    private Set<String> normalize(List<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null) continue;
            String tag = s.trim().toLowerCase(Locale.ROOT).replaceAll("[,\\s]+", "-").replaceAll("^-+|-+$", "");
            if (tag.isEmpty()) continue;
            if (tag.length() > MAX_TAG_LENGTH) throw ApiException.badRequest("A tag can be at most " + MAX_TAG_LENGTH + " characters.");
            out.add(tag);
        }
        if (out.size() > MAX_TAGS) throw ApiException.badRequest("A ticket can have at most " + MAX_TAGS + " tags.");
        return out;
    }
}
