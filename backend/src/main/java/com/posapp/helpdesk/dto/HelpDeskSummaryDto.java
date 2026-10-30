package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskPriority;
import com.posapp.helpdesk.HelpDeskStatus;

import java.time.Instant;
import java.util.List;

/** The Help Desk overview: what needs attention now and how the last 30 days went. */
public record HelpDeskSummaryDto(
        int open, int inProgress, int waiting, int resolvedNotClosed,
        int unassigned, int overdue, int dueSoon, int unanswered, int mine,
        int createdLast7, int resolvedLast7,
        Double avgFirstResponseHours, Double avgResolutionHours, Double slaCompliancePercent,
        Double avgRating, int ratingsLast30,
        List<PriorityCount> byPriority, List<AgeBucket> byAge, List<AgentLoad> agents, List<AttentionLine> attention) {

    public record PriorityCount(HelpDeskPriority priority, int count) {
    }

    public record AgeBucket(String label, int count) {
    }

    public record AgentLoad(Long userId, String name, int open, int overdue, int resolvedLast30) {
    }

    public record AttentionLine(Long id, String ticketNumber, String subject, HelpDeskPriority priority, HelpDeskStatus status, String assignedTo,
                                String reason, Instant due) {
    }
}
