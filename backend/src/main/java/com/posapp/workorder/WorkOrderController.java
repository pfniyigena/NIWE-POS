package com.posapp.workorder;

import com.posapp.security.AppUserPrincipal;
import com.posapp.workorder.WorkOrderService.Actor;
import com.posapp.workorder.dto.WorkOrderDtos.*;
import com.posapp.workorder.dto.WorkOrderRequests.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Work orders. Everyone signed in sees and works the jobs assigned to them ({@code /mine}, and the detail of their own jobs). Seeing every job
 * follows the Operations area (view); creating, assigning, costing, reopening and cancelling follows Operations (manage); invoicing follows
 * Billing (manage).
 */
@RestController
@RequestMapping("/api/work-orders")
@PreAuthorize("isAuthenticated()")
public class WorkOrderController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String BILL = "hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE')";

    private final WorkOrderService service;
    private final WorkOrderExtrasService extras;
    private final MaintenanceScheduleService schedules;

    public WorkOrderController(WorkOrderService service, WorkOrderExtrasService extras, MaintenanceScheduleService schedules) {
        this.service = service;
        this.extras = extras;
        this.schedules = schedules;
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manage = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean view = manage || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), view, manage);
    }

    // ---- lists

    @PreAuthorize(VIEW)
    @GetMapping
    public List<WorkOrderSummary> list(@RequestParam(required = false) WorkOrderStatus status, @RequestParam(required = false) WorkOrderType type,
                                       @RequestParam(required = false) Long assetId, @RequestParam(required = false) Long assigneeId,
                                       @RequestParam(required = false) Long customerId, @RequestParam(defaultValue = "false") boolean overdue,
                                       @RequestParam(required = false) String q) {
        return service.list(status, type, assetId, assigneeId, customerId, overdue, q);
    }

    @GetMapping("/mine")
    public List<WorkOrderSummary> mine(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.mine(me.getUser());
    }

    @GetMapping("/summary")
    public SummaryDto summary(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.summary(me.getUser());
    }

    // ---- service packages (declared before /{id} so the path is not read as an id)

    @PreAuthorize(VIEW)
    @GetMapping("/packages")
    public List<PackageSummary> packages(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.packages(activeOnly);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/packages/{id}")
    public PackageDetail packageDetail(@PathVariable Long id) {
        return service.packageDetail(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/packages")
    public PackageDetail createPackage(@RequestBody PackageRequest request) {
        return service.savePackage(null, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/packages/{id}")
    public PackageDetail updatePackage(@PathVariable Long id, @RequestBody PackageRequest request) {
        return service.savePackage(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/packages/{id}")
    public void deletePackage(@PathVariable Long id) {
        service.deletePackage(id);
    }

    // ---- one work order

    @GetMapping("/{id}")
    public WorkOrderDetail detail(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.detail(actor(me), id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping
    public WorkOrderDetail create(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody WorkOrderRequest request) {
        return service.create(actor(me), request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public WorkOrderDetail update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody WorkOrderRequest request) {
        return service.update(actor(me), id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        service.delete(actor(me), id);
    }

    // ---- parts, checklist, costs

    @PutMapping("/{id}/lines")
    public WorkOrderDetail lines(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody LinesRequest request) {
        return service.saveLines(actor(me), id, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/checklist")
    public WorkOrderDetail checklist(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ChecklistRequest request) {
        return service.saveChecklist(actor(me), id, request);
    }

    @PostMapping("/{id}/checklist/{itemId}/toggle")
    public WorkOrderDetail toggle(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long itemId) {
        return service.toggleChecklist(actor(me), id, itemId);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/costs")
    public WorkOrderDetail addCost(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody CostRequest request) {
        return service.addCost(actor(me), id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/costs/{costId}")
    public WorkOrderDetail deleteCost(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long costId) {
        return service.deleteCost(actor(me), id, costId);
    }

    // ---- status

    @PostMapping("/{id}/start")
    public WorkOrderDetail start(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.start(actor(me), id);
    }

    @PostMapping("/{id}/hold")
    public WorkOrderDetail hold(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.hold(actor(me), id);
    }

    @PostMapping("/{id}/complete")
    public WorkOrderDetail complete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) CompleteRequest request) {
        WorkOrderDetail d = service.complete(actor(me), id, request);
        extras.afterComplete(id);
        return d;
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/reopen")
    public WorkOrderDetail reopen(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.reopen(actor(me), id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/cancel")
    public WorkOrderDetail cancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.cancel(actor(me), id);
    }

    // ---- manufacturing

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/manufacturing-order")
    public WorkOrderDetail linkManufacturing(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ManufacturingRequest request) {
        return service.linkManufacturing(actor(me), id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/manufacturing-order")
    public WorkOrderDetail unlinkManufacturing(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.unlinkManufacturing(actor(me), id);
    }

    // ---- billing

    @PreAuthorize(BILL)
    @GetMapping("/{id}/invoice-preview")
    public InvoicePreview invoicePreview(@PathVariable Long id) {
        return service.invoicePreview(id);
    }

    @PreAuthorize(BILL)
    @PostMapping("/{id}/invoice")
    public InvoiceResult invoice(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) InvoiceRequestBody request) {
        return service.createInvoice(actor(me), id, request);
    }

    @PreAuthorize(BILL)
    @PostMapping("/{id}/release-invoice")
    public WorkOrderDetail release(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.releaseInvoice(actor(me), id);
    }

    // ---- v1.15.0: settings, preventive maintenance schedules (declared before /{id})
    @PreAuthorize(VIEW)
    @GetMapping("/settings")
    public SettingsDto settings() {
        return extras.settings();
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/settings")
    public SettingsDto saveSettings(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody SettingsRequest request) {
        return extras.saveSettings(actor(me), request);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/schedules")
    public List<ScheduleDto> schedules() {
        return schedules.list();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/schedules/{id}")
    public ScheduleDto schedule(@PathVariable Long id) {
        return schedules.get(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/schedules")
    public ScheduleDto createSchedule(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody ScheduleRequest request) {
        return schedules.save(actor(me), null, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/schedules/{id}")
    public ScheduleDto updateSchedule(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ScheduleRequest request) {
        return schedules.save(actor(me), id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/schedules/{id}/pause")
    public ScheduleDto pauseSchedule(@PathVariable Long id) {
        return schedules.setActive(id, false);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/schedules/{id}/resume")
    public ScheduleDto resumeSchedule(@PathVariable Long id) {
        return schedules.setActive(id, true);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/schedules/{id}/run")
    public ScheduleRunResult runSchedule(@PathVariable Long id) {
        return schedules.runNow(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/schedules/run-due")
    public ScheduleRunResult runDueSchedules() {
        return schedules.runDueNow();
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/schedules/{id}")
    public void deleteSchedule(@PathVariable Long id) {
        schedules.delete(id);
    }

    // ---- v1.15.0: one work order's estimate, notes, files, history, documents
    @PutMapping("/{id}/reschedule")
    @PreAuthorize(MANAGE)
    public WorkOrderDetail reschedule(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody RescheduleRequest request) {
        return service.reschedule(actor(me), id, request);
    }

    @GetMapping("/{id}/estimate")
    @PreAuthorize(VIEW)
    public EstimateDto estimate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return extras.getEstimate(actor(me), id);
    }

    @PutMapping("/{id}/estimate")
    @PreAuthorize(MANAGE)
    public EstimateDto saveEstimateSettings(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody EstimateSettingsRequest request) {
        return extras.saveEstimateSettings(actor(me), id, request);
    }

    @PostMapping("/{id}/estimate/send")
    @PreAuthorize(MANAGE)
    public EstimateDto sendEstimate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) EstimateSendRequest request) {
        return extras.sendEstimate(actor(me), id, request);
    }

    @PostMapping("/{id}/estimate/approve")
    @PreAuthorize(MANAGE)
    public EstimateDto approve(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ApprovalDecisionRequest request) {
        return extras.approve(actor(me), id, request);
    }

    @PostMapping("/{id}/estimate/reject")
    @PreAuthorize(MANAGE)
    public EstimateDto reject(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) ApprovalDecisionRequest request) {
        return extras.reject(actor(me), id, request);
    }

    @GetMapping("/{id}/notes")
    public List<NoteDto> notes(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return extras.notes(actor(me), id);
    }

    @PostMapping("/{id}/notes")
    public NoteDto addNote(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody NoteRequest request) {
        return extras.addNote(actor(me), id, request);
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    public void deleteNote(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long noteId) {
        extras.deleteNote(actor(me), id, noteId);
    }

    @GetMapping("/{id}/attachments")
    public List<AttachmentDto> attachments(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return extras.attachments(actor(me), id);
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentDto addAttachment(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "caption", required = false) String caption) throws IOException {
        return extras.addAttachment(actor(me), id, file, caption);
    }

    /** Photos are shown inline; anything else is a download. Never sniffed: the type is the one checked when it was added. */
    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> attachment(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long attachmentId) {
        WorkOrderExtrasService.FileData f = extras.file(actor(me), id, attachmentId);
        boolean inline = f.contentType().startsWith("image/") || f.contentType().equals("application/pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(f.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(f.data());
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public void deleteAttachment(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long attachmentId) {
        extras.deleteAttachment(actor(me), id, attachmentId);
    }

    @GetMapping("/{id}/history")
    public List<HistoryDto> history(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return extras.history(actor(me), id);
    }

    /** kind = jobcard (default), estimate or completion. Opened in a new tab to view and print, like the other documents. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestParam(defaultValue = "jobcard") String kind) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(extras.document(actor(me), id, kind));
    }

    @PostMapping("/{id}/email-completion")
    @PreAuthorize(MANAGE)
    public WorkOrderExtrasService.Result emailCompletion(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return extras.emailCompletion(actor(me), id);
    }
}
