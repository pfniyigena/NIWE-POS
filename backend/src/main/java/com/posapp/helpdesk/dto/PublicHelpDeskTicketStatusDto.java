package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskStatus;

import java.time.Instant;
import java.util.List;

public record PublicHelpDeskTicketStatusDto(
        String ticketNumber,
        String subject,
        HelpDeskStatus status,
        String categoryName,
        Instant createdAt,
        Instant resolvedAt,
        Instant closedAt,
        String resolutionNotes,
        List<PublicHelpDeskCommentDto> updates,
        boolean canReply,
        boolean canRate,
        Integer rating,
        String ratingComment,
        List<PublicAttachmentDto> files
) {
    public record PublicAttachmentDto(Long id, String fileName, String contentType, long sizeBytes, boolean fromRequester, Instant createdAt) {
    }
}
