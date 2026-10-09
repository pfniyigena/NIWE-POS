package com.posapp.recurringinvoice;

import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.recurringinvoice.dto.RecurringInvoiceTemplateDto;
import com.posapp.recurringinvoice.dto.RecurringInvoiceTemplateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin > Sales > Recurring invoices — ADMIN-only, same tier as Invoices/Subscriptions. */
@RestController
@RequestMapping("/api/recurring-invoices")
@PreAuthorize("hasRole('ADMIN')")
public class RecurringInvoiceTemplateController {

    private final RecurringInvoiceTemplateService service;

    public RecurringInvoiceTemplateController(RecurringInvoiceTemplateService service) {
        this.service = service;
    }

    /** Optional `?customerId=` narrows this to one customer's own templates, same convention as SubscriptionController.listAll. */
    @GetMapping
    public List<RecurringInvoiceTemplateDto> listAll(@RequestParam(required = false) Long customerId) {
        return service.listAll(customerId);
    }

    @GetMapping("/{id}")
    public RecurringInvoiceTemplateDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public RecurringInvoiceTemplateDto create(@Valid @RequestBody RecurringInvoiceTemplateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public RecurringInvoiceTemplateDto update(@PathVariable Long id, @Valid @RequestBody RecurringInvoiceTemplateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/active")
    public RecurringInvoiceTemplateDto setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return service.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    /** Generates this template's next invoice right now, without waiting for the daily scheduled sweep. */
    @PostMapping("/{id}/run-now")
    public RecurringInvoiceTemplateDto runNow(@PathVariable Long id) {
        return service.runNow(id);
    }

    /** This template's own history — every invoice it has ever generated. */
    @GetMapping("/{id}/invoices")
    public List<InvoiceDto> generatedInvoices(@PathVariable Long id) {
        return service.generatedInvoices(id);
    }
}
