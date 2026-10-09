package com.posapp.helpdesk;

import com.posapp.helpdesk.dto.HelpDeskTicketReportRow;
import com.posapp.reports.ReportExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Fourier: "Add Helpdesk reports(XLS,PDF)" — one small controller under the same {@code
 * /api/reports/**} prefix (and reusing the same {@code ReportExportService}) every other report
 * in the app already shares — see {@code com.posapp.reports.ReportController}'s own class
 * javadoc for that shared shape ({@code from}/{@code to}/{@code format=json|xlsx|pdf}).
 * Deliberately its own controller in {@code com.posapp.helpdesk} rather than one more endpoint
 * bolted onto that already-600-line class: Help Desk stays self-contained in its own package,
 * same as every other screen of this module, and Spring has no trouble with two
 * {@code @RestController}s sharing a path prefix as long as their own mappings don't collide.
 * {@code from}/{@code to} only — no status/priority/category/assignee filter, since those already
 * have a working-list home ({@code HelpDeskTicketController#search}); a report is a period export,
 * not the filtered day-to-day queue.
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN')")
public class HelpDeskReportController {

    private final HelpDeskTicketService ticketService;
    private final ReportExportService exportService;

    public HelpDeskReportController(HelpDeskTicketService ticketService, ReportExportService exportService) {
        this.ticketService = ticketService;
        this.exportService = exportService;
    }

    @GetMapping("/helpdesk-tickets")
    public ResponseEntity<?> helpdeskTickets(@RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to,
                                              @RequestParam(defaultValue = "json") String format) {
        List<HelpDeskTicketReportRow> rows = ticketService.reportRows(start(from), end(to));
        if (isDownload(format)) {
            List<String> headers = List.of("Ticket #", "Type", "Subject", "Category", "Priority", "Status", "Requester", "Assigned to", "Created", "Resolved", "Closed");
            List<List<String>> body = new ArrayList<>();
            for (HelpDeskTicketReportRow r : rows) {
                body.add(List.of(
                        r.ticketNumber(), r.type(), r.subject(), orDash(r.categoryName()), r.priority(), r.status(),
                        orDash(r.requesterName()), orDash(r.assignedToUserFullName()),
                        String.valueOf(r.createdAt()),
                        r.resolvedAt() == null ? "—" : String.valueOf(r.resolvedAt()),
                        r.closedAt() == null ? "—" : String.valueOf(r.closedAt())
                ));
            }
            return export("Help Desk Tickets", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    // ---- helpers — intentionally duplicated from ReportController's own tiny private helpers
    // rather than extracted into a shared base class, same self-contained-module choice as the
    // rest of this package.

    /** Defaults to "the last 30 days" if no range is given, same as ReportController.start. */
    private Instant start(LocalDate from) {
        LocalDate effective = from != null ? from : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        return effective.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant end(LocalDate to) {
        LocalDate effective = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        return effective.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
    }

    private boolean isDownload(String format) {
        return "xlsx".equalsIgnoreCase(format) || "pdf".equalsIgnoreCase(format);
    }

    private ResponseEntity<byte[]> export(String title, List<String> headers, List<List<String>> rows, String format) {
        byte[] content = "xlsx".equalsIgnoreCase(format)
                ? exportService.toXlsx(title, headers, rows)
                : exportService.toPdf(title, headers, rows);
        String filename = title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ("xlsx".equalsIgnoreCase(format) ? ".xlsx" : ".pdf");
        MediaType mediaType = "xlsx".equalsIgnoreCase(format)
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(content);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
