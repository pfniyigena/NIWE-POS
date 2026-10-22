package com.posapp.law;

import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawBillingDtos.OverviewRow;
import com.posapp.law.dto.LawCollectionDtos.CollectionRow;
import com.posapp.law.dto.LawDtos.ClientRow;
import com.posapp.law.dto.LawDtos.ContactDto;
import com.posapp.law.dto.LawDtos.DeadlineDto;
import com.posapp.law.dto.LawDtos.MatterRow;
import com.posapp.law.dto.LawDtos.TaskDto;
import com.posapp.law.LawTrustService.BalanceLine;
import com.posapp.law.LawTrustService.LedgerLine;
import com.posapp.reports.ReportExportService;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /api/law/export/...}: the Law Firm lists as Excel (default), PDF or CSV, with the same filters as the screens and always the whole list. Each export follows the
 * access rule of the screen it comes from, and the service behind it already leaves out the matters the person may not see.
 */
@RestController
@RequestMapping("/api/law/export")
@PreAuthorize("isAuthenticated()")
public class LawListExportController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final LawService service;
    private final LawWorkService work;
    private final LawBillingService billing;
    private final LawTrustService trust;
    private final LawCollectionService collections;
    private final LawAccess access;
    private final ReportExportService exportService;

    public LawListExportController(LawService service, LawWorkService work, LawBillingService billing, LawTrustService trust, LawCollectionService collections, LawAccess access,
                                    ReportExportService exportService) {
        this.service = service;
        this.work = work;
        this.billing = billing;
        this.trust = trust;
        this.collections = collections;
        this.access = access;
        this.exportService = exportService;
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), admin, viewer, manager);
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String yes(boolean b) {
        return b ? "Yes" : "";
    }

    /** ON_HOLD becomes "On hold". */
    private static String nice(Enum<?> e) {
        if (e == null) return "";
        String t = e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static String nice(String name) {
        if (name == null || name.isBlank()) return "";
        String t = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/matters")
    public ResponseEntity<byte[]> matters(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) MatterStatus status, @RequestParam(required = false) String q,
                                          @RequestParam(required = false) String practiceArea, @RequestParam(required = false) Long leadId, @RequestParam(required = false) Long clientId,
                                          @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Number", "Matter", "Client", "Practice area", "Jurisdiction", "Status", "Lead lawyer", "Opened", "Closed", "Billing", "Restricted", "Conflicts cleared",
                "Open tasks", "Late tasks", "Next deadline", "Late deadlines");
        List<List<String>> body = new ArrayList<>();
        for (MatterRow r : service.list(actor(me), status, q, practiceArea, leadId, clientId)) {
            body.add(List.of(s(r.number()), s(r.title()), s(r.clientName()), s(r.practiceArea()), s(r.jurisdiction()), nice(r.status()), s(r.leadName()), s(r.openedDate()), s(r.closedDate()),
                    nice(r.billingType()), yes(r.restricted()), yes(r.conflictCleared()), s(r.openTasks()), s(r.overdueTasks()), s(r.nextDeadline()), s(r.overdueDeadlines())));
        }
        return file("Matters", headers, body, format);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/clients")
    public ResponseEntity<byte[]> clients(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Client", "Phone", "E-mail", "Matters", "Open matters");
        List<List<String>> body = new ArrayList<>();
        for (ClientRow r : service.clients(actor(me))) body.add(List.of(s(r.name()), s(r.phone()), s(r.email()), s(r.matters()), s(r.openMatters())));
        return file("Clients", headers, body, format);
    }

    @GetMapping("/contacts")
    public ResponseEntity<byte[]> contacts(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) String q, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Name", "Type", "Phone", "E-mail", "ID number", "Address", "Matters", "Notes");
        List<List<String>> body = new ArrayList<>();
        for (ContactDto r : service.contacts(actor(me), q)) body.add(List.of(s(r.name()), nice(r.type()), s(r.phone()), s(r.email()), s(r.idNumber()), s(r.address()), s(r.matters()), s(r.notes())));
        return file("Contacts", headers, body, format);
    }

    @GetMapping("/deadlines")
    public ResponseEntity<byte[]> deadlines(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "false") boolean mine, @RequestParam(required = false) Long assigneeId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "false") boolean includeDone, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Date", "Time", "What", "Type", "Matter", "Matter title", "Assigned to", "Status", "Overdue", "Days left", "Notes");
        List<List<String>> body = new ArrayList<>();
        for (DeadlineDto r : work.deadlines(actor(me), mine, assigneeId, from, to, includeDone)) {
            body.add(List.of(s(r.dueDate()), s(r.dueTime()), s(r.title()), nice(r.type()), s(r.matterNumber()), s(r.matterTitle()), s(r.assignedToName()), nice(r.status()), yes(r.overdue()),
                    s(r.daysLeft()), s(r.notes())));
        }
        return file("Deadlines", headers, body, format);
    }

    @GetMapping("/tasks")
    public ResponseEntity<byte[]> tasks(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "false") boolean mine, @RequestParam(required = false) Long assigneeId,
                                        @RequestParam(defaultValue = "false") boolean includeDone, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Due", "Task", "Priority", "Status", "Matter", "Matter title", "Assigned to", "Late");
        List<List<String>> body = new ArrayList<>();
        for (TaskDto r : work.tasks(actor(me), mine, assigneeId, includeDone)) {
            body.add(List.of(s(r.dueDate()), s(r.title()), nice(r.priority()), nice(r.status()), s(r.matterNumber()), s(r.matterTitle()), s(r.assignedToName()), yes(r.overdue())));
        }
        return file("Tasks", headers, body, format);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/billing")
    public ResponseEntity<byte[]> billing(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "true") boolean openOnly, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Matter", "Title", "Client", "Billing", "Status", "Hours", "Time to bill", "Costs to bill", "Stages to bill", "Billed", "Paid", "Owed", "Retainer left");
        List<List<String>> body = new ArrayList<>();
        for (OverviewRow r : billing.overview(actor(me), openOnly)) {
            body.add(List.of(s(r.number()), r.restricted() ? "(restricted)" : s(r.title()), r.restricted() ? "" : s(r.clientName()), nice(r.billingType()), nice(r.status()), s(r.hoursLogged()),
                    s(r.unbilledTime()), s(r.unbilledCosts()), s(r.stagesPlanned()), s(r.billed()), s(r.paid()), s(r.outstanding()), s(r.retainerBalance())));
        }
        return file("Billing", headers, body, format);
    }

    /** Unpaid fees with their age (matters the person may see). */
    @PreAuthorize(VIEW)
    @GetMapping("/collections")
    public ResponseEntity<byte[]> collections(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "true") boolean overdueOnly, @RequestParam(required = false) String q,
                                              @RequestParam(required = false) Long clientId, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Invoice", "Invoice date", "Due", "Due date worked out", "Matter", "Matter title", "Client", "Client e-mail", "Amount", "Paid", "Still to pay", "Days overdue", "Reminders sent", "Last reminder");
        List<List<String>> body = new ArrayList<>();
        for (CollectionRow r : collections.list(actor(me), false, overdueOnly, q, clientId).rows()) {
            body.add(List.of(s(r.invoiceNumber()), s(r.invoiceDate()), s(r.dueDate()), yes(r.dueAssumed()), s(r.matterNumber()), s(r.matterTitle()), s(r.clientName()), s(r.clientEmail()),
                    s(r.amount()), s(r.paid()), s(r.outstanding()), r.daysOverdue() > 0 ? s(r.daysOverdue()) : "", r.reminderCount() > 0 ? s(r.reminderCount()) : "",
                    r.lastRemindedAt() == null ? "" : s(r.lastRemindedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate())));
        }
        return file("Unpaid fees", headers, body, format);
    }

    /** What the firm holds for each client today (matters the person may see). */
    @PreAuthorize(VIEW)
    @GetMapping("/trust-balances")
    public ResponseEntity<byte[]> trustBalances(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "true") boolean heldOnly, @RequestParam(defaultValue = "xlsx") String format) {
        Actor a = actor(me);
        List<String> headers = List.of("Matter", "Title", "Client", "Matter status", "Held", "Waiting to be paid out", "Available", "Last activity");
        List<List<String>> body = new ArrayList<>();
        for (BalanceLine r : trust.balanceLines(m -> access.canView(m, a), heldOnly)) {
            body.add(List.of(s(r.matterNumber()), s(r.matterTitle()), s(r.clientName()), nice(r.matterStatus()), s(r.balance()), s(r.pending()), s(r.balance().subtract(r.pending())), s(r.lastActivity())));
        }
        return file("Client money balances", headers, body, format);
    }

    /** The posted client-money movements of a period with each matter's running balance. */
    @PreAuthorize(VIEW)
    @GetMapping("/trust-ledger")
    public ResponseEntity<byte[]> trustLedger(@AuthenticationPrincipal AppUserPrincipal me,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "xlsx") String format) {
        Actor a = actor(me);
        List<String> headers = List.of("Date", "No.", "Matter", "Client", "What", "Party", "Reference", "Money in", "Money out", "Balance");
        List<List<String>> body = new ArrayList<>();
        for (LedgerLine r : trust.ledgerLines(m -> access.canView(m, a), from, to)) {
            body.add(List.of(s(r.date()), s(r.number()), s(r.matterNumber()), s(r.clientName()), nice(r.type()), s(r.party()), s(r.reference()), s(r.moneyIn()), s(r.moneyOut()), s(r.balance())));
        }
        return file("Client money ledger", headers, body, format);
    }

    private ResponseEntity<byte[]> file(String name, List<String> headers, List<List<String>> body, String format) {
        String title = "Law Firm " + name;
        String base = title.toLowerCase().replace(' ', '-');
        byte[] content;
        MediaType type;
        String ext;
        if ("csv".equalsIgnoreCase(format)) {
            content = csv(headers, body);
            type = MediaType.parseMediaType("text/csv;charset=UTF-8");
            ext = ".csv";
        } else if ("pdf".equalsIgnoreCase(format)) {
            content = exportService.toPdf(title, headers, body);
            type = MediaType.APPLICATION_PDF;
            ext = ".pdf";
        } else {
            content = exportService.toXlsx(title, headers, body);
            type = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            ext = ".xlsx";
        }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(base + ext).build().toString())
                .body(content);
    }

    /** Comma separated, every value in quotes, with a byte-order mark so Excel opens the accents right. A value that would be read as a formula is made harmless. */
    static byte[] csv(List<String> headers, List<List<String>> body) {
        StringBuilder b = new StringBuilder("﻿");
        b.append(line(headers));
        for (List<String> row : body) b.append(line(row));
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String line(List<String> cells) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) b.append(',');
            String v = cells.get(i) == null ? "" : cells.get(i);
            if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !v.matches("-?[0-9][0-9.,]*")) v = "'" + v;
            b.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        return b.append("\r\n").toString();
    }
}
