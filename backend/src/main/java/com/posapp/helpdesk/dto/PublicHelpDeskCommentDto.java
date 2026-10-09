package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskComment;

import java.time.Instant;

/** One entry on the public "check my ticket" timeline — never the author's name (an internal staff identity has no business being shown to an anonymous guest), just what was said and when. */
public record PublicHelpDeskCommentDto(String body, Instant createdAt) {
    public static PublicHelpDeskCommentDto from(HelpDeskComment c) {
        return new PublicHelpDeskCommentDto(c.getBody(), c.getCreatedAt());
    }
}
