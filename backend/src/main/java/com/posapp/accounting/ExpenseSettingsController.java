package com.posapp.accounting;

import com.posapp.accounting.dto.ExpenseExtras.SettingsDto;
import com.posapp.accounting.dto.ExpenseExtras.SettingsRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Approval limit, self-approval and budget blocking, and bill due-date defaults. Anyone with Accounting access can read; only an administrator changes. */
@RestController
@RequestMapping("/api/accounting/expense-settings")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_VIEW')")
public class ExpenseSettingsController {

    private final ExpenseSettingsService service;

    public ExpenseSettingsController(ExpenseSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public SettingsDto get() {
        return service.get();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public SettingsDto update(@RequestBody SettingsRequest request) {
        return service.update(request);
    }
}
