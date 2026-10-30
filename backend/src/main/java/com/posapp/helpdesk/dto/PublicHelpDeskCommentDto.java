package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskComment;

import java.time.Instant;

/** One update a requester may see: their own replies and the staff replies marked visible. Staff names are not shown. */
public record PublicHelpDeskCommentDto(String body, Instant createdAt, boolean fromRequester) {
    public static PublicHelpDeskCommentDto from(HelpDeskComment c) {
        return new PublicHelpDeskCommentDto(c.getBody(), c.getCreatedAt(), c.isFromRequester());
    }
}
