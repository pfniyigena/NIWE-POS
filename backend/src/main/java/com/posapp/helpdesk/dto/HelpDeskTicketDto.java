package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskCategory;
import com.posapp.helpdesk.HelpDeskPriority;
import com.posapp.helpdesk.HelpDeskSla;
import com.posapp.helpdesk.HelpDeskStatus;
import com.posapp.helpdesk.HelpDeskTicket;
import com.posapp.helpdesk.HelpDeskTicketType;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** The staff view of a ticket, with its deadlines and where it stands against them. */
public record HelpDeskTicketDto(
        Long id,
        String ticketNumber,
        HelpDeskTicketType type,
        String subject,
        String description,
        Long categoryId,
        String categoryName,
        HelpDeskPriority priority,
        HelpDeskStatus status,
        String requesterName,
        String requesterEmail,
        String requesterPhone,
        Long customerId,
        String customerName,
        Long createdByUserId,
        String createdByUserFullName,
        Long assignedToUserId,
        String assignedToUserFullName,
        String resolutionNotes,
        Instant resolvedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant firstResponseDueAt,
        Instant resolutionDueAt,
        Instant firstRespondedAt,
        Instant waitingSince,
        String responseState,
        String resolutionState,
        List<String> tags,
        Long linkedInvoiceId,
        String linkedInvoiceNumber,
        Long linkedWorkOrderId,
        String linkedWorkOrderNumber,
        Long mergedIntoId,
        String mergedIntoNumber,
        Integer satisfactionRating,
        String satisfactionComment,
        Instant ratedAt,
        Instant lastRequesterReplyAt
) {
    public static HelpDeskTicketDto from(HelpDeskTicket t) {
        return from(t, 4);
    }

    public static HelpDeskTicketDto from(HelpDeskTicket t, int warnHours) {
        HelpDeskCategory category = t.getCategory();
        Instant now = Instant.now();
        return new HelpDeskTicketDto(
                t.getId(), t.getTicketNumber(), t.getType(), t.getSubject(), t.getDescription(),
                category != null ? category.getId() : null, category != null ? category.getName() : null,
                t.getPriority(), t.getStatus(),
                t.getRequesterName(), t.getRequesterEmail(), t.getRequesterPhone(),
                t.getCustomerId(), t.getCustomerName(),
                t.getCreatedByUserId(), t.getCreatedByUserFullName(),
                t.getAssignedToUserId(), t.getAssignedToUserFullName(),
                t.getResolutionNotes(), t.getResolvedAt(), t.getClosedAt(),
                t.getCreatedAt(), t.getUpdatedAt(),
                t.getFirstResponseDueAt(), t.getResolutionDueAt(), t.getFirstRespondedAt(), t.getWaitingSince(),
                HelpDeskSla.responseState(t, now), HelpDeskSla.resolutionState(t, now, warnHours),
                tagList(t.getTags()),
                t.getLinkedInvoiceId(), t.getLinkedInvoiceNumber(), t.getLinkedWorkOrderId(), t.getLinkedWorkOrderNumber(),
                t.getMergedIntoId(), t.getMergedIntoNumber(),
                t.getSatisfactionRating(), t.getSatisfactionComment(), t.getRatedAt(), t.getLastRequesterReplyAt()
        );
    }

    public static List<String> tagList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
