package com.posapp.accounting;

import com.posapp.accounting.dto.ExpenseExtras.BulkRequest;
import com.posapp.accounting.dto.ExpenseExtras.BulkResult;
import com.posapp.accounting.dto.ExpenseExtras.Skipped;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Approve or reject several pending expenses at once. Each one is its own transaction, so one that cannot be done does not stop the rest. */
@Service
public class ExpenseBulkService {

    static final int MAX = 200;

    private final ExpenseService expenseService;
    private final ExpenseRepository repository;

    public ExpenseBulkService(ExpenseService expenseService, ExpenseRepository repository) {
        this.expenseService = expenseService;
        this.repository = repository;
    }

    public BulkResult run(BulkRequest r) {
        if (r == null || r.getIds() == null || r.getIds().isEmpty()) throw ApiException.badRequest("Choose at least one expense.");
        LinkedHashSet<Long> ids = new LinkedHashSet<>(r.getIds());
        if (ids.size() > MAX) throw ApiException.badRequest("At most " + MAX + " expenses at a time.");
        String action = r.getAction() == null ? "" : r.getAction().trim().toUpperCase();
        if (!action.equals("APPROVE") && !action.equals("REJECT")) throw ApiException.badRequest("Action must be APPROVE or REJECT.");
        int done = 0;
        List<Skipped> skipped = new ArrayList<>();
        for (Long id : ids) {
            try {
                if (action.equals("APPROVE")) expenseService.approve(id, false);
                else expenseService.reject(id, r.getReason());
                done++;
            } catch (RuntimeException ex) {
                Optional<Expense> e = repository.findById(id);
                String msg = ex instanceof ApiException || ex instanceof org.springframework.security.access.AccessDeniedException ? ex.getMessage() : "Could not be processed.";
                skipped.add(new Skipped(id, e.map(Expense::getExpenseNumber).orElse(null), msg));
            }
        }
        return new BulkResult(done, skipped);
    }
}
