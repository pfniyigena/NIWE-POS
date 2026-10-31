package com.posapp.accounting;

import com.posapp.accounting.dto.ExpenseExtras.BudgetDto;
import com.posapp.accounting.dto.ExpenseExtras.BudgetRequest;
import com.posapp.accounting.dto.ExpenseExtras.BudgetStatusRow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Monthly budgets per expense category or cost center, and how they are being used. */
@RestController
@RequestMapping("/api/accounting/expense-budgets")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_VIEW')")
public class ExpenseBudgetController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_MANAGE')";

    private final ExpenseBudgetService service;

    public ExpenseBudgetController(ExpenseBudgetService service) {
        this.service = service;
    }

    @GetMapping
    public List<BudgetDto> list(@RequestParam(required = false) Integer year) {
        return service.list(year);
    }

    /** Every budget of the year against one month (default: this month). */
    @GetMapping("/status")
    public List<BudgetStatusRow> status(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) {
        LocalDate today = LocalDate.now();
        return service.status(year != null ? year : today.getYear(), month != null ? month : today.getMonthValue());
    }

    @PreAuthorize(MANAGE)
    @PostMapping
    public BudgetDto create(@RequestBody BudgetRequest request) {
        return service.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public BudgetDto update(@PathVariable Long id, @RequestBody BudgetRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
