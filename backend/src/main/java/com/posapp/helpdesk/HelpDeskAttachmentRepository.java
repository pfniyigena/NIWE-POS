package com.posapp.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HelpDeskAttachmentRepository extends JpaRepository<HelpDeskAttachment, Long> {
    List<HelpDeskAttachment> findByTicketIdOrderByIdAsc(Long ticketId);

    long countByTicketId(Long ticketId);
}
