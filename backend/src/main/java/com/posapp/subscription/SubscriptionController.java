package com.posapp.subscription;

import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.subscription.dto.CreateSubscriptionRequest;
import com.posapp.subscription.dto.SubscriptionDto;
import com.posapp.subscription.dto.SubscriptionEventDto;
import com.posapp.subscription.dto.SubscriptionRequests;
import com.posapp.subscription.dto.SubscriptionViews;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin > Sales > Subscriptions — ADMIN-only, same tier as Invoices/Sale orders. */
@RestController
@RequestMapping("/api/subscriptions")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    /** Optional filters: customerId, status, planId, search (customer or plan name) and view (trial, due-soon, cancelling, term-ending, resuming). */
    @GetMapping
    public List<SubscriptionDto> listAll(@RequestParam(required = false) Long customerId,
                                         @RequestParam(required = false) SubscriptionStatus status,
                                         @RequestParam(required = false) Long planId,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) String view) {
        return service.listAll(customerId, status, planId, search, view);
    }

    /** The overview: recurring revenue, churn, what bills soon, what is unpaid. */
    @GetMapping("/summary")
    public SubscriptionViews.SummaryDto summary() {
        return service.summary();
    }

    @GetMapping("/settings")
    public SubscriptionViews.SettingsDto settings() {
        return service.settings();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping("/settings")
    public SubscriptionViews.SettingsDto saveSettings(@RequestBody SubscriptionRequests.SettingsRequest request) {
        return service.updateSettings(request);
    }

    @GetMapping("/{id}")
    public SubscriptionDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping
    public SubscriptionDto create(@Valid @RequestBody CreateSubscriptionRequest request) {
        return service.create(request);
    }

    /** Edits the terms from the next bill on. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping("/{id}")
    public SubscriptionDto update(@PathVariable Long id, @RequestBody SubscriptionRequests.UpdateRequest request) {
        return service.update(id, request);
    }

    /** What a plan change would do, without doing it. */
    @PostMapping("/{id}/change-plan/quote")
    public SubscriptionViews.ChangeQuoteDto quoteChange(@PathVariable Long id, @RequestBody SubscriptionRequests.ChangePlanRequest request) {
        return service.quoteChange(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/change-plan")
    public SubscriptionDto changePlan(@PathVariable Long id, @RequestBody SubscriptionRequests.ChangePlanRequest request) {
        return service.changePlan(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/pause")
    public SubscriptionDto pause(@PathVariable Long id, @RequestBody(required = false) SubscriptionRequests.PauseRequest request) {
        return request == null ? service.pause(id) : service.pause(id, request.reason(), request.resumeOn());
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/skip-next")
    public SubscriptionDto skipNext(@PathVariable Long id) {
        return service.skipNext(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/undo-cancel")
    public SubscriptionDto undoCancel(@PathVariable Long id) {
        return service.undoCancel(id);
    }

    @GetMapping("/{id}/events")
    public List<SubscriptionEventDto> events(@PathVariable Long id) {
        return service.events(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/resume")
    public SubscriptionDto resume(@PathVariable Long id) {
        return service.resume(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/cancel")
    public SubscriptionDto cancel(@PathVariable Long id, @RequestBody(required = false) SubscriptionRequests.CancelRequest request) {
        return service.cancel(id, request != null ? request.reason() : null, request != null && Boolean.TRUE.equals(request.atPeriodEnd()));
    }

    /** Bills this one subscription right now, without waiting for the daily scheduled sweep. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/bill-now")
    public SubscriptionDto billNow(@PathVariable Long id) {
        return service.billNow(id);
    }

    /** This subscription's own billing history — every invoice it has ever generated. */
    @GetMapping("/{id}/invoices")
    public List<InvoiceDto> billingHistory(@PathVariable Long id) {
        return service.billingHistory(id);
    }

    /** Fourier: "Print also subscription" — see SubscriptionService.pdf's javadoc. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = service.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
