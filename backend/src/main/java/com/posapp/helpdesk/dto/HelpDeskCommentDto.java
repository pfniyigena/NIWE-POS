package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskComment;

import java.time.Instant;

public record HelpDeskCommentDto(Long id, Long ticketId, Long authorUserId, String authorUserFullName,
                                   String body, boolean internal, Instant createdAt, boolean fromRequester, boolean emailed) {
    public static HelpDeskCommentDto from(HelpDeskComment c) {
        return new HelpDeskCommentDto(c.getId(), c.getTicket().getId(), c.getAuthorUserId(), c.getAuthorUserFullName(),
                c.getBody(), c.isInternal(), c.getCreatedAt(), c.isFromRequester(), c.isEmailed());
    }
}
