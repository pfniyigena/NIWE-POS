package com.posapp.timetracking;

import com.posapp.security.AppUserPrincipal;
import com.posapp.timetracking.dto.TimeDtos.*;
import com.posapp.timetracking.dto.TimeRequests.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Time tracking. Everyone signed in can log, submit and see their own time ({@code /my/**}). Seeing everyone's time and the projects
 * follows the Operations area (view / manage); turning approved time into an invoice follows Billing (manage).
 */
@RestController
@RequestMapping("/api/time")
@PreAuthorize("isAuthenticated()")
public class TimeTrackingController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String BILL = "hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE')";

    private final TimeTrackingService service;
    private final TimesheetDisciplineService discipline;

    public TimeTrackingController(TimeTrackingService service, TimesheetDisciplineService discipline) {
        this.service = service;
        this.discipline = discipline;
    }

    // ---- pickers, open to every signed-in user

    @GetMapping("/staff")
    public List<StaffOption> staff() {
        return service.staff();
    }

    @GetMapping("/project-choices")
    public List<ProjectChoice> projectChoices(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.projectChoices(me.getUser());
    }

    // ---- my time

    @GetMapping("/my/entries")
    public WeekDto myEntries(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return service.myEntries(me.getUser(), from, to);
    }

    @GetMapping("/my/summary")
    public MySummaryDto mySummary(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.mySummary(me.getUser());
    }

    @PostMapping("/my/entries")
    public EntryDto create(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody EntryRequest request) {
        return service.createEntry(me.getUser(), request);
    }

    @PutMapping("/my/entries/{id}")
    public EntryDto update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody EntryRequest request) {
        return service.updateEntry(me.getUser(), id, request);
    }

    @DeleteMapping("/my/entries/{id}")
    public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        service.deleteEntry(me.getUser(), id);
    }

    @PostMapping("/my/submit")
    public Map<String, Integer> submit(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody SubmitRequest request) {
        return Map.of("submitted", service.submit(me.getUser(), request));
    }

    @PostMapping("/my/copy-week")
    public CopyResult copyWeek(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody(required = false) CopyWeekRequest request) {
        service.requireModule();
        return discipline.copyWeek(me.getUser(), request == null ? null : request.week());
    }

    @GetMapping("/my/timer")
    public TimerDto timer(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.timer(me.getUser());
    }

    @PostMapping("/my/timer/start")
    public TimerDto start(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody TimerStartRequest request) {
        return service.startTimer(me.getUser(), request);
    }

    @PostMapping("/my/timer/stop")
    public EntryDto stop(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody(required = false) TimerStopRequest request) {
        return service.stopTimer(me.getUser(), request);
    }

    @DeleteMapping("/my/timer")
    public void discard(@AuthenticationPrincipal AppUserPrincipal me) {
        service.discardTimer(me.getUser());
    }

    // ---- who has not finished their week

    @PreAuthorize(VIEW)
    @GetMapping("/missing")
    public MissingDto missing(@RequestParam(required = false) LocalDate week) {
        return discipline.missing(week);
    }

    /** Stops, right now, every timer that has been running longer than the limit in the settings. */
    @PreAuthorize(MANAGE)
    @PostMapping("/timers/auto-stop")
    public Map<String, Integer> autoStop() {
        service.requireModule();
        return Map.of("stopped", discipline.autoStopLongTimers());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/reminders")
    public ReminderResult reminders(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody(required = false) ReminderRequest request) {
        return discipline.sendReminders(me.getUser(), request == null ? null : request.week(), request == null ? null : request.userIds(),
                TimeTrackingService.actorName(me.getUser()));
    }

    // ---- projects

    @PreAuthorize(VIEW)
    @GetMapping("/projects")
    public List<ProjectSummaryDto> projects(@RequestParam(required = false) ProjectStatus status, @RequestParam(required = false) Long customerId,
                                            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.projects(status, customerId, includeArchived);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/projects/{id}")
    public ProjectDetailDto project(@PathVariable Long id) {
        return service.projectDetail(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/projects")
    public ProjectDetailDto createProject(@RequestBody ProjectRequest request) {
        return service.createProject(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/projects/{id}")
    public ProjectDetailDto updateProject(@PathVariable Long id, @RequestBody ProjectRequest request) {
        return service.updateProject(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/projects/{id}")
    public void deleteProject(@PathVariable Long id) {
        service.deleteProject(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/projects/{id}/tasks")
    public TaskDto addTask(@PathVariable Long id, @RequestBody TaskRequest request) {
        return service.addTask(id, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/tasks/{taskId}")
    public TaskDto updateTask(@PathVariable Long taskId, @RequestBody TaskRequest request) {
        return service.updateTask(taskId, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/tasks/{taskId}")
    public void deleteTask(@PathVariable Long taskId) {
        service.deleteTask(taskId);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/projects/{id}/team")
    public ProjectDetailDto setTeam(@PathVariable Long id, @RequestBody TeamRequest request) {
        return service.setTeam(id, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/projects/{id}/rates")
    public List<RateDto> setRate(@PathVariable Long id, @RequestBody RateRequest request) {
        return service.setRate(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/projects/{id}/rates/{userId}")
    public List<RateDto> removeRate(@PathVariable Long id, @PathVariable Long userId) {
        return service.removeRate(id, userId);
    }

    // ---- everyone's time and approval

    @PreAuthorize(VIEW)
    @GetMapping("/entries")
    public List<EntryDto> entries(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) TimeEntryStatus status, @RequestParam(required = false) Long userId,
                                  @RequestParam(required = false) Long projectId, @RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to) {
        return service.allEntries(me.getUser(), status, userId, projectId, from, to);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/approvals")
    public List<EntryDto> approvals(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.pendingApprovals(me.getUser());
    }

    @PreAuthorize(VIEW)
    @GetMapping("/summary")
    public SummaryDto summary() {
        return service.summary();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/approve")
    public Map<String, Integer> approve(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody DecisionRequest request) {
        return Map.of("approved", service.approve(me.getUser(), request.ids()));
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/reject")
    public Map<String, Integer> reject(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody DecisionRequest request) {
        return Map.of("rejected", service.reject(me.getUser(), request.ids(), request.reason()));
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/entries/{id}")
    public EntryDto correct(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody EntryRequest request) {
        return service.managerUpdateEntry(me.getUser(), id, request);
    }

    // ---- settings

    @PreAuthorize(VIEW)
    @GetMapping("/settings")
    public SettingsDto settings() {
        return service.settings();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/settings")
    public SettingsDto updateSettings(@RequestBody SettingsRequest request) {
        return service.updateSettings(request);
    }

    // ---- billing

    @PreAuthorize(BILL)
    @GetMapping("/billing/ready")
    public List<ProjectSummaryDto> ready() {
        return service.readyToInvoice();
    }

    @PreAuthorize(BILL)
    @PostMapping("/billing/preview")
    public BillingPreviewDto preview(@RequestBody BillingRequest request) {
        return service.billingPreview(request);
    }

    @PreAuthorize(BILL)
    @PostMapping("/billing/invoice")
    public BillingResultDto invoice(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody BillingRequest request) {
        return service.invoiceTime(me.getUser(), request);
    }

    @PreAuthorize(BILL)
    @PostMapping("/billing/release/{invoiceId}")
    public Map<String, Integer> release(@PathVariable Long invoiceId) {
        return Map.of("released", service.releaseInvoice(invoiceId));
    }
}
