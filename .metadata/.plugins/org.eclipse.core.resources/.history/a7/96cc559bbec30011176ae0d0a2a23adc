package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskTicket;

import java.time.Instant;

/**
 * Fourier: "Add Helpdesk reports(XLS,PDF)" — one row per ticket created in the chosen date range,
 * backing {@code HelpDeskReportController}'s "Help Desk Tickets" report (both the on-screen table
 * and the Excel/PDF export, same {@code ReportExportService} every other report in the app uses).
 * Deliberately a flat read of the same fields {@link HelpDeskTicketDto} already exposes — kept as
 * its own record instead of reusing that DTO directly for the same reason every other report row
 * in {@code com.posapp.reports.dto} is its own record rather than a service DTO: a report row
 * answers to "what's a useful spreadsheet column," which can drift from "what's a useful detail-
 * screen field" over time even though the two happen to match exactly today.
 */
public record HelpDeskTicketReportRow(
        String ticketNumber,
        String type,
        String subject,
        String categoryName,
        String priority,
        String status,
        String requesterName,
        String assignedToUserFullName,
        Instant createdAt,
        Instant resolvedAt,
        Instant closedAt
) {
    public static HelpDeskTicketReportRow from(HelpDeskTicket t) {
        return new HelpDeskTicketReportRow(
                t.getTicketNumber(),
                t.getType().name(),
                t.getSubject(),
                t.getCategory() != null ? t.getCategory().getName() : null,
                t.getPriority().name(),
                t.getStatus().name(),
                t.getRequesterName(),
                t.getAssignedToUserFullName(),
                t.getCreatedAt(),
                t.getResolvedAt(),
                t.getClosedAt()
        );
    }
}
