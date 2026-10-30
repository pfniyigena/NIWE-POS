package com.posapp.helpdesk;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.helpdesk.dto.HelpDeskCannedDto;
import com.posapp.helpdesk.dto.HelpDeskCannedRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Saved replies. {{requester}}, {{ticket}}, {{subject}} and {{agent}} are filled in when one is used on a ticket. */
@Service
public class HelpDeskCannedService {

    private final HelpDeskCannedResponseRepository repository;
    private final HelpDeskCategoryRepository categoryRepository;
    private final HelpDeskTicketRepository ticketRepository;
    private final AuditLogService auditLogService;

    public HelpDeskCannedService(HelpDeskCannedResponseRepository repository, HelpDeskCategoryRepository categoryRepository,
                                 HelpDeskTicketRepository ticketRepository, AuditLogService auditLogService) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.ticketRepository = ticketRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<HelpDeskCannedDto> list(boolean activeOnly) {
        return repository.findAllByOrderByTitleAsc().stream().filter(c -> !activeOnly || c.isActive()).map(HelpDeskCannedDto::from).toList();
    }

    @Transactional
    public HelpDeskCannedDto create(HelpDeskCannedRequest r) {
        String title = r.getTitle().trim();
        if (repository.existsByTitleIgnoreCase(title)) throw ApiException.conflict("A saved reply called \"" + title + "\" already exists");
        HelpDeskCannedResponse c = new HelpDeskCannedResponse();
        apply(c, r, title);
        HelpDeskCannedResponse saved = repository.save(c);
        auditLogService.record("HELPDESK_CANNED_CREATED", "Created saved reply " + title, "HelpDeskCannedResponse", saved.getId());
        return HelpDeskCannedDto.from(saved);
    }

    @Transactional
    public HelpDeskCannedDto update(Long id, HelpDeskCannedRequest r) {
        HelpDeskCannedResponse c = get(id);
        String title = r.getTitle().trim();
        if (!c.getTitle().equalsIgnoreCase(title) && repository.existsByTitleIgnoreCase(title)) throw ApiException.conflict("A saved reply called \"" + title + "\" already exists");
        apply(c, r, title);
        HelpDeskCannedResponse saved = repository.save(c);
        auditLogService.record("HELPDESK_CANNED_UPDATED", "Updated saved reply " + title, "HelpDeskCannedResponse", saved.getId());
        return HelpDeskCannedDto.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        HelpDeskCannedResponse c = get(id);
        repository.delete(c);
        auditLogService.record("HELPDESK_CANNED_DELETED", "Deleted saved reply " + c.getTitle(), "HelpDeskCannedResponse", id);
    }

    /** The text with the ticket's details filled in. */
    @Transactional(readOnly = true)
    public String render(Long id, Long ticketId) {
        HelpDeskCannedResponse c = get(id);
        HelpDeskTicket t = ticketRepository.findById(ticketId).orElseThrow(() -> ApiException.notFound("Ticket not found: " + ticketId));
        String requester = t.getRequesterName() == null || t.getRequesterName().isBlank() ? "there" : t.getRequesterName();
        return c.getBody()
                .replace("{{requester}}", requester)
                .replace("{{ticket}}", t.getTicketNumber())
                .replace("{{subject}}", t.getSubject())
                .replace("{{agent}}", HelpDeskEvents.actorName());
    }

    private void apply(HelpDeskCannedResponse c, HelpDeskCannedRequest r, String title) {
        c.setTitle(title);
        c.setBody(r.getBody().trim());
        if (r.getCategoryId() != null) {
            categoryRepository.findById(r.getCategoryId()).orElseThrow(() -> ApiException.badRequest("Category not found"));
        }
        c.setCategoryId(r.getCategoryId());
        if (r.getActive() != null) c.setActive(r.getActive());
    }

    private HelpDeskCannedResponse get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Saved reply not found: " + id));
    }
}
