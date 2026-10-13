package com.posapp.recurringexpense;

import com.posapp.recurringexpense.dto.RecurringExpenseTemplateDto;
import com.posapp.recurringexpense.dto.RecurringExpenseTemplateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin > Accounting > Recurring expenses — ADMIN-only, same tier as every other Accounting setup screen. */
@RestController
@RequestMapping("/api/accounting/recurring-expenses")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_VIEW')")
public class RecurringExpenseTemplateController {

    private final RecurringExpenseTemplateService service;

    public RecurringExpenseTemplateController(RecurringExpenseTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecurringExpenseTemplateDto> listAll() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    public RecurringExpenseTemplateDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE')")
    @PostMapping
    public RecurringExpenseTemplateDto create(@Valid @RequestBody RecurringExpenseTemplateRequest request) {
        return service.create(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE')")
    @PutMapping("/{id}")
    public RecurringExpenseTemplateDto update(@PathVariable Long id, @Valid @RequestBody RecurringExpenseTemplateRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE')")
    @PostMapping("/{id}/active")
    public RecurringExpenseTemplateDto setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return service.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    /** Generates this template's next expense right now, without waiting for the daily scheduled sweep. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE')")
    @PostMapping("/{id}/run-now")
    public RecurringExpenseTemplateDto runNow(@PathVariable Long id) {
        return service.runNow(id);
    }
}
