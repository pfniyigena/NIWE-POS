package com.posapp.subscription;

import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.subscription.dto.CancelSubscriptionRequest;
import com.posapp.subscription.dto.CreateSubscriptionRequest;
import com.posapp.subscription.dto.SubscriptionDto;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin > Sales > Subscriptions — ADMIN-only, same tier as Invoices/Sale orders. */
@RestController
@RequestMapping("/api/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    /** Fourier: "on the customer ... view button" convention every other module's own listAll follows — optional `?customerId=` narrows this to one customer. */
    @GetMapping
    public List<SubscriptionDto> listAll(@RequestParam(required = false) Long customerId) {
        return service.listAll(customerId);
    }

    @GetMapping("/{id}")
    public SubscriptionDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public SubscriptionDto create(@Valid @RequestBody CreateSubscriptionRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/pause")
    public SubscriptionDto pause(@PathVariable Long id) {
        return service.pause(id);
    }

    @PostMapping("/{id}/resume")
    public SubscriptionDto resume(@PathVariable Long id) {
        return service.resume(id);
    }

    @PostMapping("/{id}/cancel")
    public SubscriptionDto cancel(@PathVariable Long id, @RequestBody(required = false) CancelSubscriptionRequest request) {
        return service.cancel(id, request != null ? request.getReason() : null);
    }

    /** Bills this one subscription right now, without waiting for the daily scheduled sweep. */
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
