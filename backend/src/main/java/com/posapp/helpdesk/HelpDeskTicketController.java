package com.posapp.helpdesk;

import com.posapp.helpdesk.dto.AddHelpDeskCommentRequest;
import com.posapp.helpdesk.dto.AssignTicketRequest;
import com.posapp.helpdesk.dto.HelpDeskCommentDto;
import com.posapp.helpdesk.dto.HelpDeskAttachmentDto;
import com.posapp.helpdesk.dto.HelpDeskEventDto;
import com.posapp.helpdesk.dto.HelpDeskSummaryDto;
import com.posapp.helpdesk.dto.HelpDeskTicketDto;
import com.posapp.helpdesk.dto.HelpDeskWorkflowRequests;
import com.posapp.helpdesk.dto.HelpDeskTicketRequest;
import com.posapp.helpdesk.dto.ResolveTicketRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.List;

/** Admin > Help Desk > Tickets — ADMIN-only, same tier as Subscriptions/Purchases/Sales. */
@RestController
@RequestMapping("/api/helpdesk/tickets")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class HelpDeskTicketController {

    private final HelpDeskTicketService service;
    private final HelpDeskWorkflowService workflow;
    private final HelpDeskOverviewService overview;
    private final HelpDeskEvents events;
    private final HelpDeskAttachmentService attachments;
    private final HelpDeskSlaService sla;

    public HelpDeskTicketController(HelpDeskTicketService service, HelpDeskWorkflowService workflow, HelpDeskOverviewService overview,
                                    HelpDeskEvents events, HelpDeskAttachmentService attachments, HelpDeskSlaService sla) {
        this.service = service;
        this.workflow = workflow;
        this.overview = overview;
        this.events = events;
        this.attachments = attachments;
        this.sla = sla;
    }

    @GetMapping
    public List<HelpDeskTicketDto> search(@RequestParam(required = false) HelpDeskTicketType type,
                                            @RequestParam(required = false) HelpDeskStatus status,
                                            @RequestParam(required = false) HelpDeskPriority priority,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) Long assignedToUserId,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String view,
                                            @RequestParam(required = false) String tag) {
        return service.search(type, status, priority, categoryId, assignedToUserId, q, view, tag);
    }

    @GetMapping("/summary")
    public HelpDeskSummaryDto summary() {
        return overview.summary();
    }

    @GetMapping("/{id}")
    public HelpDeskTicketDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping
    public HelpDeskTicketDto create(@Valid @RequestBody HelpDeskTicketRequest request) {
        return service.createByStaff(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping("/{id}")
    public HelpDeskTicketDto update(@PathVariable Long id, @Valid @RequestBody HelpDeskTicketRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/assign")
    public HelpDeskTicketDto assign(@PathVariable Long id, @RequestBody(required = false) AssignTicketRequest request) {
        return service.assign(id, request != null ? request.getAssignedToUserId() : null);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/start")
    public HelpDeskTicketDto start(@PathVariable Long id) {
        return service.start(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/resolve")
    public HelpDeskTicketDto resolve(@PathVariable Long id, @RequestBody(required = false) ResolveTicketRequest request) {
        return service.resolve(id, request != null ? request.getResolutionNotes() : null);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/close")
    public HelpDeskTicketDto close(@PathVariable Long id) {
        return service.close(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/reopen")
    public HelpDeskTicketDto reopen(@PathVariable Long id) {
        return service.reopen(id);
    }

    /** Fourier: "print tick[et] on PDF" — see HelpDeskTicketService.pdf's own javadoc. No Content-Disposition, same as InvoiceController/{id}/pdf: the frontend opens this in a new tab to view/print, not as a forced download. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = service.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    @GetMapping("/{id}/comments")
    public List<HelpDeskCommentDto> listComments(@PathVariable Long id) {
        return service.listComments(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/comments")
    public HelpDeskCommentDto addComment(@PathVariable Long id, @Valid @RequestBody AddHelpDeskCommentRequest request) {
        return service.addComment(id, request);
    }

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/wait")
    public HelpDeskTicketDto waitForCustomer(@PathVariable Long id) {
        return service.waitForCustomer(id);
    }

    @GetMapping("/{id}/events")
    public List<HelpDeskEventDto> events(@PathVariable Long id) {
        service.getById(id);
        return events.list(id);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/tags")
    public HelpDeskTicketDto tags(@PathVariable Long id, @RequestBody HelpDeskWorkflowRequests.TagsRequest request) {
        return workflow.setTags(id, request.getTags());
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/links")
    public HelpDeskTicketDto links(@PathVariable Long id, @RequestBody HelpDeskWorkflowRequests.LinksRequest request) {
        return workflow.setLinks(id, request.getInvoiceNumber(), request.getWorkOrderNumber());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/merge")
    public HelpDeskTicketDto merge(@PathVariable Long id, @RequestBody HelpDeskWorkflowRequests.MergeRequest request) {
        return workflow.merge(id, request.getTargetId());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bulk")
    public HelpDeskWorkflowRequests.BulkResult bulk(@RequestBody HelpDeskWorkflowRequests.BulkRequest request) {
        return workflow.bulk(request);
    }

    /** Runs the deadline check and the auto-close now (it also runs by itself every five minutes). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/sla/run")
    public HelpDeskSlaService.RunResult runSla() {
        return sla.run(java.time.Instant.now());
    }

    @GetMapping("/{id}/attachments")
    public List<HelpDeskAttachmentDto> attachments(@PathVariable Long id) {
        return attachments.list(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HelpDeskAttachmentDto addAttachment(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                               @RequestParam(defaultValue = "false") boolean visible) throws IOException {
        return attachments.addByStaff(id, file, visible);
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> attachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        HelpDeskAttachmentService.FileData f = attachments.file(id, attachmentId);
        boolean inline = f.contentType().startsWith("image/") || f.contentType().equals("application/pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(f.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.data());
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/attachments/{attachmentId}/visible")
    public void setVisible(@PathVariable Long id, @PathVariable Long attachmentId, @RequestParam boolean value) {
        attachments.setVisible(id, attachmentId, value);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public void deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        attachments.delete(id, attachmentId);
    }
}
