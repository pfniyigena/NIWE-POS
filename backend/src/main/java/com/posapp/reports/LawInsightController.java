package com.posapp.reports;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * The second set of law firm reports (see {@link LawInsightService}). Same rules as {@link ReportController}: admin or the Reports area, {@code format} is
 * {@code json} (on screen), {@code xlsx} or {@code pdf}.
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_REPORTS_VIEW')")
public class LawInsightController {

    private final LawInsightService service;
    private final ReportExportService exportService;

    public LawInsightController(LawInsightService service, ReportExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    private static boolean isDownload(String format) {
        return "xlsx".equalsIgnoreCase(format) || "pdf".equalsIgnoreCase(format);
    }

    private ResponseEntity<byte[]> export(String title, List<String> headers, List<List<String>> rows, String format) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        byte[] content = xlsx ? exportService.toXlsx(title, headers, rows) : exportService.toPdf(title, headers, rows);
        String filename = title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + (xlsx ? ".xlsx" : ".pdf");
        return ResponseEntity.ok()
                .contentType(xlsx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(content);
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String dash(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }

    private static String pct(BigDecimal v) {
        return v == null ? "—" : v.toPlainString() + "%";
    }

    @GetMapping("/law-productivity")
    public ResponseEntity<?> productivity(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                          @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = service.productivity(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Lawyer", "Matters", "Hours", "Billable hours", "Non-billable hours", "Billable %", "Billable value", "Billed", "Not yet billed");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.lawyer(), s(r.matters()), s(r.hours()), s(r.billableHours()), s(r.nonBillableHours()), pct(r.billablePercent()), s(r.billableValue()),
                        s(r.billedValue()), s(r.notYetBilledValue())));
            }
            return export("Law Lawyer Productivity", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-profitability")
    public ResponseEntity<?> profitability(@RequestParam(defaultValue = "true") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        var rows = service.profitability(openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Matter", "Title", "Client", "Billing", "Status", "Opened", "Days open", "Hours", "Value of time", "Fees billed", "Billed", "Paid",
                    "Owed", "Collected %", "Cost of time", "Costs paid out", "Costs recovered", "Profit", "Profit %", "Hours without a cost rate", "Not yet billed");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.number(), r.title(), dash(r.client()), r.billing(), r.status(), s(r.opened()), s(r.daysOpen()), s(r.hours()), s(r.timeValue()), s(r.feesBilled()),
                        s(r.billed()), s(r.paid()), s(r.outstanding()), pct(r.collectedPercent()), s(r.timeCost()), s(r.costsPaidOut()), s(r.costsRecovered()),
                        r.profit() == null ? "—" : s(r.profit()), pct(r.profitPercent()), s(r.hoursWithoutCostRate()), s(r.notYetBilled())));
            }
            return export("Law Matter Profitability", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-ageing")
    public ResponseEntity<?> ageing(@RequestParam(defaultValue = "json") String format) {
        var rows = service.ageing();
        if (isDownload(format)) {
            List<String> headers = List.of("Matter", "Title", "Client", "Invoice", "Invoice date", "Days", "Billed", "Outstanding", "0-30 days", "31-60 days", "61-90 days", "Over 90 days");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.matter(), r.title(), dash(r.client()), dash(r.invoice()), s(r.invoiceDate()), s(r.days()), s(r.billed()), s(r.outstanding()), s(r.band0to30()),
                        s(r.band31to60()), s(r.band61to90()), s(r.bandOver90())));
            }
            return export("Law Unpaid Invoices by Age", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-documents-portal")
    public ResponseEntity<?> documentsAndPortal(@RequestParam(defaultValue = "true") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        var rows = service.documentsAndPortal(openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Matter", "Title", "Client", "Status", "Documents", "Storage (MB)", "Confidential", "Shared with client", "Waiting for review", "Portal",
                    "People with access", "Last client sign-in", "Unanswered messages");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.number(), r.title(), dash(r.client()), r.status(), s(r.documents()), s(r.storageMb()), s(r.confidential()), s(r.sharedWithClient()),
                        s(r.waitingForReview()), r.portal(), s(r.clientPeople()), dash(r.lastClientSignIn()), s(r.unansweredMessages())));
            }
            return export("Law Documents and Client Portal", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-conflict-log")
    public ResponseEntity<?> conflictLog(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                         @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        var rows = service.conflictLog(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("When", "Checked by", "Searched for", "Matter", "Hits", "Result");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.when(), dash(r.checkedBy()), dash(r.searched()), dash(r.matter()), s(r.hits()), r.result()));
            }
            return export("Law Conflict Check Log", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/law-caseload")
    public ResponseEntity<?> caseload(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                      @RequestParam(defaultValue = "json") String format) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.withDayOfYear(1);
        var rows = service.caseload(start, end);
        if (isDownload(format)) {
            List<String> headers = List.of("Practice area", "Court or forum", "Opened", "Closed", "Open now", "On hold", "Average days to close");
            List<List<String>> body = new ArrayList<>();
            for (var r : rows) {
                body.add(List.of(r.practiceArea(), dash(r.court()), s(r.opened()), s(r.closed()), s(r.openNow()), s(r.onHold()), r.averageDaysToClose() == null ? "—" : r.averageDaysToClose().toPlainString()));
            }
            return export("Law Caseload", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }
}
