package com.posapp.helpdesk;

import com.posapp.helpdesk.dto.AddHelpDeskCommentRequest;
import com.posapp.helpdesk.dto.AssignTicketRequest;
import com.posapp.helpdesk.dto.HelpDeskCommentDto;
import com.posapp.helpdesk.dto.HelpDeskTicketDto;
import com.posapp.helpdesk.dto.HelpDeskTicketRequest;
import com.posapp.helpdesk.dto.ResolveTicketRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin > Help Desk > Tickets — ADMIN-only, same tier as Subscriptions/Purchases/Sales. */
@RestController
@RequestMapping("/api/helpdesk/tickets")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class HelpDeskTicketController {

    private final HelpDeskTicketService service;

    public HelpDeskTicketController(HelpDeskTicketService service) {
        this.service = service;
    }

    @GetMapping
    public List<HelpDeskTicketDto> search(@RequestParam(required = false) HelpDeskTicketType type,
                                            @RequestParam(required = false) HelpDeskStatus status,
                                            @RequestParam(required = false) HelpDeskPriority priority,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) Long assignedToUserId,
                                            @RequestParam(required = false) String q) {
        return service.search(type, status, priority, categoryId, assignedToUserId, q);
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
}
