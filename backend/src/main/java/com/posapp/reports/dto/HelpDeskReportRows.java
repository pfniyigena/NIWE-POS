package com.posapp.reports.dto;

import java.time.Instant;

/** Row types of the Help Desk reports, used by {@code HelpDeskReportService} and the matching {@code ReportController} endpoints. */
public final class HelpDeskReportRows {

    private HelpDeskReportRows() {
    }

    /** Deadlines kept and missed for one priority (tickets created in the period that had deadlines). */
    public record SlaRow(String priority, int tickets, int responseMet, int responseMissed, Double avgFirstResponseHours,
                         int resolved, int resolutionMet, int resolutionMissed, Double avgResolutionHours, Double compliancePercent) {
    }

    public record AgentRow(String agent, int assigned, int open, int resolved, int overdue, Double avgResolutionHours, Double avgRating) {
    }

    public record CategoryRow(String category, int tickets, int open, int resolved, Double avgResolutionHours, Double avgRating) {
    }

    public record SatisfactionRow(String ticketNumber, String subject, String requester, String agent, int rating, String comment, Instant ratedAt) {
    }

    public record OverdueRow(String ticketNumber, String subject, String priority, String status, String agent, String deadline, Instant due, long hoursLate) {
    }
}
