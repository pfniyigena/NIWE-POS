package com.posapp.installment;

import com.posapp.installment.dto.InstallmentDtos.*;
import com.posapp.installment.dto.InstallmentRequests.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Customer instalment plans on invoices. Follows the Billing area: view to look, manage to change. */
@RestController
@RequestMapping("/api/installments")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_VIEW')")
public class InstallmentController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE')";

    private final InstallmentService service;

    public InstallmentController(InstallmentService service) {
        this.service = service;
    }

    @GetMapping("/plans")
    public List<PlanSummaryDto> list(@RequestParam(required = false) InstallmentPlanStatus status, @RequestParam(required = false) Long customerId) {
        return service.list(status, customerId);
    }

    @GetMapping("/plans/{id}")
    public PlanDetailDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/by-invoice/{invoiceId}")
    public List<PlanSummaryDto> forInvoice(@PathVariable Long invoiceId) {
        return service.forInvoice(invoiceId);
    }

    @GetMapping("/summary")
    public SummaryDto summary() {
        return service.summary();
    }

    @GetMapping("/settings")
    public SettingsDto settings() {
        return service.settings();
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/settings")
    public SettingsDto updateSettings(@RequestBody SettingsRequest request) {
        return service.updateSettings(request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/preview")
    public PreviewDto preview(@RequestBody PlanRequest request) {
        return service.preview(request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/plans")
    public PlanDetailDto create(@RequestBody PlanRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        return service.create(request, principal.getUser());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/plans/{id}/payments")
    public PlanDetailDto pay(@PathVariable Long id, @RequestBody PaymentRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        return service.recordPayment(id, request, principal.getUser());
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/plans/{id}/schedule")
    public PlanDetailDto reschedule(@PathVariable Long id, @RequestBody RescheduleRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        return service.reschedule(id, request, principal.getUser());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/plans/{id}/cancel")
    public PlanDetailDto cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        return service.cancel(id, request != null ? request.reason() : null);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/plans/{id}/installments/{seq}/waive-fee")
    public PlanDetailDto waiveFee(@PathVariable Long id, @PathVariable int seq) {
        return service.waiveLateFee(id, seq);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/plans/{id}/remind")
    public ReminderResultDto remind(@PathVariable Long id) {
        return service.remindNow(id);
    }

    @GetMapping("/plans/{id}/schedule-pdf")
    public ResponseEntity<byte[]> schedulePdf(@PathVariable Long id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"instalment-schedule-" + id + ".pdf\"")
                .body(service.schedulePdf(id));
    }
}
