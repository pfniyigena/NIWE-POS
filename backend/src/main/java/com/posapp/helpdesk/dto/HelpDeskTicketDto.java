package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskCategory;
import com.posapp.helpdesk.HelpDeskPriority;
import com.posapp.helpdesk.HelpDeskStatus;
import com.posapp.helpdesk.HelpDeskTicket;
import com.posapp.helpdesk.HelpDeskTicketType;

import java.time.Instant;

/** One Help Desk ticket, admin list row and detail payload both — same single-DTO convention as SubscriptionDto/InvoiceDto. */
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
        Instant updatedAt
) {
    public static HelpDeskTicketDto from(HelpDeskTicket t) {
        HelpDeskCategory category = t.getCategory();
        return new HelpDeskTicketDto(
                t.getId(), t.getTicketNumber(), t.getType(), t.getSubject(), t.getDescription(),
                category != null ? category.getId() : null, category != null ? category.getName() : null,
                t.getPriority(), t.getStatus(),
                t.getRequesterName(), t.getRequesterEmail(), t.getRequesterPhone(),
                t.getCustomerId(), t.getCustomerName(),
                t.getCreatedByUserId(), t.getCreatedByUserFullName(),
                t.getAssignedToUserId(), t.getAssignedToUserFullName(),
                t.getResolutionNotes(), t.getResolvedAt(), t.getClosedAt(),
                t.getCreatedAt(), t.getUpdatedAt()
        );
    }
}
