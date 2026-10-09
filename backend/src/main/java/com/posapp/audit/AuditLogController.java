package com.posapp.audit;

import com.posapp.audit.dto.AuditLogDto;
import com.posapp.common.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;

/**
 * Task: "Implement an audit log of who did what and when" — the admin-only viewer for what
 * {@link AuditLogService} records. ADMIN-only at the class level: unlike a receipt's letterhead
 * logos or a terminal's own settings, there's no cashier-facing reason to ever expose this.
 */
@RestController
@RequestMapping("/api/audit-log")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * {@code from}/{@code to} are plain calendar dates (matching {@code ReportController}'s own
     * convention), converted to inclusive {@code Instant} bounds against the server's own
     * time zone — unlike reports, an unset range here means "no bound" rather than defaulting to
     * the last 30 days, since paging (newest-first, capped at {@code size}) already keeps an
     * unfiltered request from returning the whole table at once.
     */
    @GetMapping
    public PageResponse<AuditLogDto> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) String action,
                                             @RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1) : null;
        return auditLogService.search(q, action, fromInstant, toInstant, page, size);
    }

    /** Powers the viewer's action-type filter dropdown. */
    @GetMapping("/actions")
    public List<String> listActions() {
        return auditLogService.listDistinctActions();
    }
}
