package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskStatus;

import java.time.Instant;
import java.util.List;

/**
 * The public lookup page's own result (Fourier: "supporting ... guest ticket creation" implies
 * being able to check on it afterward too, same "look it up by its number" pattern as the
 * existing public reservation feature). No internal fields — no assignee name, no internal
 * comments (see {@code HelpDeskCommentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc}),
 * nothing about who else uses this system.
 */
public record PublicHelpDeskTicketStatusDto(
        String ticketNumber,
        String subject,
        HelpDeskStatus status,
        String categoryName,
        Instant createdAt,
        Instant resolvedAt,
        Instant closedAt,
        String resolutionNotes,
        List<PublicHelpDeskCommentDto> updates
) {
}
