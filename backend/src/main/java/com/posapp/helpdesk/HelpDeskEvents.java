package com.posapp.helpdesk;

import com.posapp.helpdesk.dto.HelpDeskEventDto;
import com.posapp.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Writes and reads a ticket's history. */
@Service
public class HelpDeskEvents {

    private final HelpDeskEventRepository repository;

    public HelpDeskEvents(HelpDeskEventRepository repository) {
        this.repository = repository;
    }

    /** Recorded under the signed-in user, or "System" for the nightly job. */
    @Transactional
    public void record(HelpDeskTicket ticket, HelpDeskEventKind kind, String detail) {
        record(ticket, kind, detail, actorName());
    }

    @Transactional
    public void record(HelpDeskTicket ticket, HelpDeskEventKind kind, String detail, String by) {
        HelpDeskEvent e = new HelpDeskEvent();
        e.setTicket(ticket);
        e.setKind(kind);
        e.setDetail(detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail);
        e.setCreatedByName(by);
        repository.save(e);
    }

    @Transactional(readOnly = true)
    public List<HelpDeskEventDto> list(Long ticketId) {
        return repository.findByTicketIdOrderByIdDesc(ticketId).stream().map(HelpDeskEventDto::from).toList();
    }

    public static String actorName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p && p.getUser() != null) {
            return p.getUser().getFullName();
        }
        return "System";
    }
}
