package com.posapp.accounting;

import com.posapp.accounting.dto.ExpenseDto;
import com.posapp.accounting.dto.ExpenseRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Admin-only for logging/editing, same shape {@code StockAdjustmentController} already settled
 * on for its own maker-checker split: {@link #approve}/{@link #reject}/{@link #voidExpense}/
 * {@link #settle} below keep their own additive override, letting an ADMIN delegate just the
 * sign-off step — not the logging step — to someone granted {@code Permission.APPROVE_EXPENSE}.
 */
@RestController
@RequestMapping("/api/accounting/expenses")
@PreAuthorize("hasRole('ADMIN')")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public List<ExpenseDto> listAll() {
        return expenseService.listAll();
    }

    @GetMapping("/{id}")
    public ExpenseDto getById(@PathVariable Long id) {
        return expenseService.getById(id);
    }

    @PostMapping
    public ExpenseDto create(@Valid @RequestBody ExpenseRequest request) {
        return expenseService.create(request);
    }

    /** Only while PENDING — see ExpenseService.update. */
    @PutMapping("/{id}")
    public ExpenseDto update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(id, request);
    }

    /** ADMIN or PERM_APPROVE_EXPENSE — moves a PENDING expense to APPROVED, which is what actually posts it to Accounting. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_EXPENSE')")
    @PostMapping("/{id}/approve")
    public ExpenseDto approve(@PathVariable Long id) {
        return expenseService.approve(id);
    }

    /** ADMIN or PERM_APPROVE_EXPENSE — discards a PENDING expense with no Accounting impact. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_EXPENSE')")
    @PostMapping("/{id}/reject")
    public ExpenseDto reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return expenseService.reject(id, body != null ? body.get("reason") : null);
    }

    /** ADMIN or PERM_APPROVE_EXPENSE — reverses an already-APPROVED expense's posting. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_EXPENSE')")
    @PostMapping("/{id}/void")
    public ExpenseDto voidExpense(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return expenseService.voidExpense(id, body != null ? body.get("reason") : null);
    }

    /** Records that an APPROVED, still-unpaid bill has since been paid off — see ExpenseService.settle. Open to any admin (not maker-checker gated — settling is a separate real-world event, not a sign-off on the original claim). */
    @PostMapping("/{id}/settle")
    public ExpenseDto settle(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        LocalDate settledDate = body != null && body.get("settledDate") != null ? LocalDate.parse(body.get("settledDate")) : null;
        return expenseService.settle(id, settledDate);
    }
}
