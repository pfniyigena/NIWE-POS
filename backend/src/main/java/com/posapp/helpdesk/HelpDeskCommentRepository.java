package com.posapp.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HelpDeskCommentRepository extends JpaRepository<HelpDeskComment, Long> {

    List<HelpDeskComment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    /** The public lookup page's own timeline — see HelpDeskComment.internal's javadoc for why only these are ever shown to a requester. */
    List<HelpDeskComment> findByTicketIdAndInternalFalseOrderByCreatedAtAsc(Long ticketId);
}
