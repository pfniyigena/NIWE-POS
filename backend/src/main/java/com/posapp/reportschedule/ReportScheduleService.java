package com.posapp.reportschedule;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.inventory.ReorderService;
import com.posapp.inventory.dto.ReorderSuggestionDto;
import com.posapp.reports.ReportController;
import com.posapp.reports.ReportExportService;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Saved report schedules: CRUD for the admin screen plus the engine that emails due reports. A cheap check
 * runs every five minutes; a schedule goes out once on each day it is due, from its run time onward.
 */
@Service
public class ReportScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduleService.class);
    private static final Set<String> FORMATS = Set.of("XLSX", "PDF");
    private static final Set<String> FREQUENCIES = Set.of("DAILY", "WEEKLY", "MONTHLY");

    private final ReportScheduleRepository repository;
    private final ReportController reportController;
    private final ReportExportService exportService;
    private final ReorderService reorderService;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;

    public ReportScheduleService(ReportScheduleRepository repository,
                                 ReportController reportController,
                                 ReportExportService exportService,
                                 ReorderService reorderService,
                                 MailService mailService,
                                 StoreSettingsService storeSettingsService,
                                 AuditLogService auditLogService) {
        this.repository = repository;
        this.reportController = reportController;
        this.exportService = exportService;
        this.reorderService = reorderService;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
    }

    // ---- CRUD ----

    @Transactional(readOnly = true)
    public List<ReportScheduleDto> list() {
        return repository.findAllByOrderByNameAsc().stream().map(ReportScheduleDto::from).toList();
    }

    @Transactional
    public ReportScheduleDto create(ReportScheduleRequest request) {
        ReportSchedule s = new ReportSchedule();
        apply(s, request);
        ReportSchedule saved = repository.save(s);
        auditLogService.record("REPORT_SCHEDULE_CREATED", "Created report schedule '" + saved.getName() + "' ("
                + saved.getReportKey() + ", " + saved.getFrequency() + ")", "ReportSchedule", saved.getId());
        return ReportScheduleDto.from(saved);
    }

    @Transactional
    public ReportScheduleDto update(Long id, ReportScheduleRequest request) {
        ReportSchedule s = getOrThrow(id);
        apply(s, request);
        ReportSchedule saved = repository.save(s);
        auditLogService.record("REPORT_SCHEDULE_UPDATED", "Updated report schedule '" + saved.getName() + "'", "ReportSchedule", saved.getId());
        return ReportScheduleDto.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        ReportSchedule s = getOrThrow(id);
        repository.delete(s);
        auditLogService.record("REPORT_SCHEDULE_DELETED", "Deleted report schedule '" + s.getName() + "'", "ReportSchedule", id);
    }

    private ReportSchedule getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Report schedule not found"));
    }

    private void apply(ReportSchedule s, ReportScheduleRequest r) {
        ScheduledReportCatalog.Def def = ScheduledReportCatalog.find(r.getReportKey());
        if (def == null) {
            throw ApiException.badRequest("Unknown report: " + r.getReportKey());
        }
        String format = r.getFormat() == null ? "XLSX" : r.getFormat().trim().toUpperCase();
        if (!FORMATS.contains(format)) {
            throw ApiException.badRequest("Format must be XLSX or PDF");
        }
        String frequency = r.getFrequency() == null ? "DAILY" : r.getFrequency().trim().toUpperCase();
        if (!FREQUENCIES.contains(frequency)) {
            throw ApiException.badRequest("Frequency must be DAILY, WEEKLY or MONTHLY");
        }
        String runTime = r.getRunTime() == null || r.getRunTime().isBlank() ? "07:00" : r.getRunTime().trim();
        if (!runTime.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            throw ApiException.badRequest("Run time must look like 07:00 (24-hour HH:mm)");
        }
        Integer dayOfWeek = null;
        Integer dayOfMonth = null;
        if (frequency.equals("WEEKLY")) {
            dayOfWeek = r.getDayOfWeek() == null ? 1 : r.getDayOfWeek();
            if (dayOfWeek < 1 || dayOfWeek > 7) throw ApiException.badRequest("Day of week must be 1 (Monday) to 7 (Sunday)");
        }
        if (frequency.equals("MONTHLY")) {
            dayOfMonth = r.getDayOfMonth() == null ? 1 : r.getDayOfMonth();
            if (dayOfMonth < 1 || dayOfMonth > 28) throw ApiException.badRequest("Day of month must be between 1 and 28");
        }
        String recipients = null;
        if (r.getRecipients() != null && !r.getRecipients().isBlank()) {
            List<String> cleaned = new ArrayList<>();
            for (String part : r.getRecipients().split("[,;\\s]+")) {
                if (part.isBlank()) continue;
                if (!part.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                    throw ApiException.badRequest("Not a valid email address: " + part);
                }
                cleaned.add(part);
            }
            recipients = cleaned.isEmpty() ? null : String.join(", ", cleaned);
        }
        Integer window = def.daysFilter() ? r.getDayWindow() : null;
        if (window != null && (window < 1 || window > 3650)) {
            throw ApiException.badRequest("Days must be between 1 and 3650");
        }
        s.setName(r.getName().trim());
        s.setReportKey(def.key());
        s.setFormat(format);
        s.setFrequency(frequency);
        s.setRunTime(runTime);
        s.setDayOfWeek(dayOfWeek);
        s.setDayOfMonth(dayOfMonth);
        s.setRecipients(recipients);
        s.setCategoryId(def.categoryFilter() ? r.getCategoryId() : null);
        s.setWarehouseId(def.warehouseFilter() ? r.getWarehouseId() : null);
        s.setBranchId(def.branchFilter() ? r.getBranchId() : null);
        s.setDayWindow(window);
        s.setActive(r.isActive());
    }

    // ---- engine ----

    @Scheduled(fixedDelay = 300_000L, initialDelay = 90_000L)
    public void tick() {
        try {
            List<ReportSchedule> active = repository.findByActiveTrue();
            if (active.isEmpty()) return;
            StoreSettings settings = storeSettingsService.getSettingsEntity();
            LocalDateTime now = LocalDateTime.now(zoneOf(settings));
            for (ReportSchedule s : active) {
                if (isDue(s, now)) {
                    runAndRecord(s, now.toLocalDate());
                }
            }
        } catch (Exception ex) {
            log.warn("Report schedule check failed: {}", ex.getMessage(), ex);
        }
    }

    /** "Run now" on the admin screen: sends the report immediately, whatever its schedule. */
    public ReportScheduleDto runNow(Long id) {
        ReportSchedule s = getOrThrow(id);
        LocalDate today = LocalDateTime.now(zoneOf(storeSettingsService.getSettingsEntity())).toLocalDate();
        return ReportScheduleDto.from(runAndRecord(s, today));
    }

    private boolean isDue(ReportSchedule s, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (today.equals(s.getLastRunDate())) return false;
        if (now.toLocalTime().isBefore(LocalTime.parse(s.getRunTime()))) return false;
        return switch (s.getFrequency()) {
            case "WEEKLY" -> s.getDayOfWeek() != null && today.getDayOfWeek() == DayOfWeek.of(s.getDayOfWeek());
            case "MONTHLY" -> s.getDayOfMonth() != null && today.getDayOfMonth() == s.getDayOfMonth();
            default -> true;
        };
    }

    private ReportSchedule runAndRecord(ReportSchedule s, LocalDate today) {
        // Mark the day first so a failing schedule is not retried every five minutes.
        s.setLastRunDate(today);
        s.setLastRunAt(Instant.now());
        repository.save(s);
        String status;
        String message;
        try {
            message = deliver(s, today);
            status = "OK";
        } catch (SkippedException ex) {
            status = "SKIPPED";
            message = ex.getMessage();
        } catch (Exception ex) {
            log.warn("Scheduled report '{}' failed: {}", s.getName(), ex.getMessage(), ex);
            status = "FAILED";
            message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
        s.setLastStatus(status);
        s.setLastMessage(message != null && message.length() > 480 ? message.substring(0, 480) : message);
        ReportSchedule saved = repository.save(s);
        auditLogService.record("REPORT_SCHEDULE_RUN", "Report schedule '" + s.getName() + "': " + status + " — " + message,
                "ReportSchedule", s.getId());
        return saved;
    }

    private static final class SkippedException extends RuntimeException {
        SkippedException(String message) {
            super(message);
        }
    }

    private String deliver(ReportSchedule s, LocalDate today) {
        ScheduledReportCatalog.Def def = ScheduledReportCatalog.find(s.getReportKey());
        if (def == null) throw new IllegalStateException("The report '" + s.getReportKey() + "' no longer exists");
        StoreSettings settings = storeSettingsService.getSettingsEntity();
        List<String> to = recipientsOf(s, settings);
        if (to.isEmpty()) throw new SkippedException("No recipients: add one, or set the notifications email in Settings");
        if (!mailService.isConfigured()) throw new SkippedException("Email is not configured (Admin > Settings > Email)");

        boolean xlsx = "XLSX".equals(s.getFormat());
        String ext = xlsx ? "xlsx" : "pdf";
        String periodLabel = "";
        byte[] bytes;
        if (def.invoker() == null) {
            // reorder-list is built here because it has no report endpoint of its own.
            List<ReorderSuggestionDto> rows = reorderService.suggestions();
            List<String> headers = List.of("SKU", "Product", "Category", "Sellable", "On order", "Reorder level", "Max level", "Suggested qty");
            List<List<String>> body = new ArrayList<>();
            for (ReorderSuggestionDto r : rows) {
                body.add(List.of(r.sku() == null ? "—" : r.sku(), r.productName(), r.categoryName() == null ? "—" : r.categoryName(),
                        String.valueOf(r.onHand()), String.valueOf(r.onOrder()), String.valueOf(r.reorderLevel()),
                        String.valueOf(r.maxStockLevel()), String.valueOf(r.suggestedQuantity())));
            }
            bytes = xlsx ? exportService.toXlsx("Reorder list", headers, body) : exportService.toPdf("Reorder list", headers, body);
        } else {
            LocalDate from = null;
            LocalDate end = null;
            if (def.periodic()) {
                switch (s.getFrequency()) {
                    case "WEEKLY" -> { from = today.minusDays(7); end = today.minusDays(1); }
                    case "MONTHLY" -> { from = today.minusMonths(1).withDayOfMonth(1); end = today.withDayOfMonth(1).minusDays(1); }
                    default -> { from = today.minusDays(1); end = from; }
                }
                periodLabel = from.equals(end) ? from.toString() : from + " to " + end;
            }
            ScheduledReportCatalog.Params params = new ScheduledReportCatalog.Params(from, end, s.getCategoryId(),
                    s.getWarehouseId(), s.getBranchId(), s.getDayWindow(), ext);
            ResponseEntity<?> response = callAsSystem(def, params);
            if (!(response.getBody() instanceof byte[] file)) {
                throw new IllegalStateException("The report did not produce a file");
            }
            bytes = file;
        }

        String fileBase = def.label().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") + "-" + today;
        String contentType = xlsx ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/pdf";
        String subject = s.getName() + (periodLabel.isEmpty() ? "" : " (" + periodLabel + ")");
        String html = "<p>Attached is the scheduled report <b>" + escape(def.label()) + "</b>"
                + (periodLabel.isEmpty() ? "" : " for " + escape(periodLabel)) + ".</p>"
                + "<p>Schedule: " + escape(s.getName()) + " (" + s.getFrequency().toLowerCase() + ").</p>";
        List<String> failed = new ArrayList<>();
        for (String address : to) {
            try {
                mailService.send(address, subject, html, List.of(new EmailAttachment(fileBase + "." + ext, contentType, bytes)));
            } catch (Exception ex) {
                failed.add(address + " (" + ex.getMessage() + ")");
            }
        }
        if (failed.size() == to.size()) {
            throw new IllegalStateException("Could not email any recipient: " + String.join("; ", failed));
        }
        return "Sent to " + (to.size() - failed.size()) + " recipient(s)" + (failed.isEmpty() ? "" : "; failed: " + String.join("; ", failed));
    }

    /** The report endpoints are ADMIN-only, so the call is made under a short-lived system admin identity. */
    private ResponseEntity<?> callAsSystem(ScheduledReportCatalog.Def def, ScheduledReportCatalog.Params params) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext system = SecurityContextHolder.createEmptyContext();
        system.setAuthentication(new UsernamePasswordAuthenticationToken("system", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(system);
        try {
            return def.invoker().call(reportController, params);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private static List<String> recipientsOf(ReportSchedule s, StoreSettings settings) {
        String raw = s.getRecipients() != null && !s.getRecipients().isBlank() ? s.getRecipients() : settings.getNotificationsEmail();
        List<String> result = new ArrayList<>();
        if (raw != null) {
            for (String part : raw.split("[,;\\s]+")) {
                if (!part.isBlank()) result.add(part.trim());
            }
        }
        return result;
    }

    private static ZoneId zoneOf(StoreSettings settings) {
        try {
            if (settings.getTimeZone() != null && !settings.getTimeZone().isBlank()) {
                return ZoneId.of(settings.getTimeZone().trim());
            }
        } catch (Exception ignored) {
            // fall back to the server's own zone
        }
        return ZoneId.systemDefault();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
