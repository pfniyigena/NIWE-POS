package com.posapp.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HelpDeskEventRepository extends JpaRepository<HelpDeskEvent, Long> {
    List<HelpDeskEvent> findByTicketIdOrderByIdDesc(Long ticketId);

    List<HelpDeskEvent> findByTicketId(Long ticketId);
}
