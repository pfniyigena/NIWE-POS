package com.posapp.reportschedule;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/report-schedules")
@PreAuthorize("hasRole('ADMIN')")
public class ReportScheduleController {

    /** What the screen needs to build its form: one entry per schedulable report and which filters it supports. */
    public record ReportOption(String key, String label, String section, boolean periodic, boolean categoryFilter,
                               boolean warehouseFilter, boolean branchFilter, boolean daysFilter, Integer defaultDays) {
    }

    private final ReportScheduleService service;

    public ReportScheduleController(ReportScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReportScheduleDto> list() {
        return service.list();
    }

    @GetMapping("/reports")
    public List<ReportOption> reports() {
        return ScheduledReportCatalog.all().stream()
                .map(d -> new ReportOption(d.key(), d.label(), d.section(), d.periodic(), d.categoryFilter(),
                        d.warehouseFilter(), d.branchFilter(), d.daysFilter(), d.defaultDays()))
                .toList();
    }

    @PostMapping
    public ReportScheduleDto create(@Valid @RequestBody ReportScheduleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ReportScheduleDto update(@PathVariable Long id, @Valid @RequestBody ReportScheduleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/run")
    public ReportScheduleDto run(@PathVariable Long id) {
        return service.runNow(id);
    }
}
