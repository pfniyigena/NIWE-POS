package com.posapp.accounting;

import com.posapp.accounting.dto.ExpenseExtras.BudgetDto;
import com.posapp.accounting.dto.ExpenseExtras.BudgetRequest;
import com.posapp.accounting.dto.ExpenseExtras.BudgetStatusRow;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Monthly spending budgets per expense category or cost center. "Spent" counts approved expenses without the recoverable VAT;
 * "pending" counts those still waiting for approval.
 */
@Service
public class ExpenseBudgetService {

    private final ExpenseBudgetRepository repository;
    private final ExpenseRepository expenseRepository;
    private final AccountRepository accountRepository;
    private final CostCenterService costCenterService;
    private final AuditLogService auditLogService;

    public ExpenseBudgetService(ExpenseBudgetRepository repository, ExpenseRepository expenseRepository, AccountRepository accountRepository,
                                CostCenterService costCenterService, AuditLogService auditLogService) {
        this.repository = repository;
        this.expenseRepository = expenseRepository;
        this.accountRepository = accountRepository;
        this.costCenterService = costCenterService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<BudgetDto> list(Integer year) {
        int y = year != null ? year : LocalDate.now().getYear();
        return repository.findByBudgetYearOrderByIdAsc(y).stream().map(ExpenseBudgetService::toDto).toList();
    }

    @Transactional
    public BudgetDto create(BudgetRequest r) {
        int year = r.getYear() != null ? r.getYear() : LocalDate.now().getYear();
        if (year < 2000 || year > 2100) throw ApiException.badRequest("Choose a year between 2000 and 2100.");
        if (r.getMonthlyAmount() == null || r.getMonthlyAmount().signum() <= 0) throw ApiException.badRequest("The monthly budget must be more than zero.");
        if ((r.getAccountId() == null) == (r.getCostCenterId() == null)) {
            throw ApiException.badRequest("Choose either an expense category or a cost center for the budget, not both and not neither.");
        }
        ExpenseBudget b = new ExpenseBudget();
        b.setBudgetYear(year);
        b.setMonthlyAmount(r.getMonthlyAmount());
        b.setNote(r.getNote() == null || r.getNote().isBlank() ? null : r.getNote().trim());
        if (r.getAccountId() != null) {
            Account a = accountRepository.findById(r.getAccountId()).orElseThrow(() -> ApiException.notFound("Account not found: " + r.getAccountId()));
            if (a.getType() != AccountType.EXPENSE) throw ApiException.badRequest("'" + a.getName() + "' is not an Expense account.");
            if (repository.findByAccountIdAndBudgetYear(a.getId(), year).isPresent()) {
                throw ApiException.badRequest(a.getName() + " already has a budget for " + year + ". Edit that one instead.");
            }
            b.setAccount(a);
        } else {
            CostCenter c = costCenterService.getEntityOrThrow(r.getCostCenterId());
            if (repository.findByCostCenterIdAndBudgetYear(c.getId(), year).isPresent()) {
                throw ApiException.badRequest(c.getName() + " already has a budget for " + year + ". Edit that one instead.");
            }
            b.setCostCenter(c);
        }
        ExpenseBudget saved = repository.save(b);
        auditLogService.record("EXPENSE_BUDGET_SET", "Monthly budget " + saved.getMonthlyAmount() + " for " + label(saved) + " in " + year, "ExpenseBudget", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public BudgetDto update(Long id, BudgetRequest r) {
        ExpenseBudget b = repository.findById(id).orElseThrow(() -> ApiException.notFound("Budget not found: " + id));
        if (r.getMonthlyAmount() == null || r.getMonthlyAmount().signum() <= 0) throw ApiException.badRequest("The monthly budget must be more than zero.");
        b.setMonthlyAmount(r.getMonthlyAmount());
        b.setNote(r.getNote() == null || r.getNote().isBlank() ? null : r.getNote().trim());
        ExpenseBudget saved = repository.save(b);
        auditLogService.record("EXPENSE_BUDGET_CHANGED", "Monthly budget for " + label(saved) + " is now " + saved.getMonthlyAmount(), "ExpenseBudget", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        ExpenseBudget b = repository.findById(id).orElseThrow(() -> ApiException.notFound("Budget not found: " + id));
        repository.delete(b);
        auditLogService.record("EXPENSE_BUDGET_REMOVED", "Removed the budget for " + label(b) + " in " + b.getBudgetYear(), "ExpenseBudget", id);
    }

    /** Every budget of the year against the given month. */
    @Transactional(readOnly = true)
    public List<BudgetStatusRow> status(int year, int month) {
        if (month < 1 || month > 12) throw ApiException.badRequest("Month must be 1 to 12.");
        YearMonth ym = YearMonth.of(year, month);
        List<Expense> approved = expenseRepository.findByStatusAndExpenseDateBetweenOrderByExpenseDateAsc(ExpenseStatus.APPROVED, ym.atDay(1), ym.atEndOfMonth());
        List<Expense> pending = expenseRepository.findByStatusAndExpenseDateBetweenOrderByExpenseDateAsc(ExpenseStatus.PENDING, ym.atDay(1), ym.atEndOfMonth());
        List<BudgetStatusRow> rows = new ArrayList<>();
        for (ExpenseBudget b : repository.findByBudgetYearOrderByIdAsc(year)) {
            BigDecimal spent = sum(approved, b);
            BigDecimal wait = sum(pending, b);
            rows.add(row(b, spent, wait));
        }
        return rows;
    }

    /** Rows that are over or close to the limit, for the overview. */
    @Transactional(readOnly = true)
    public List<BudgetStatusRow> alerts(int year, int month) {
        return status(year, month).stream().filter(r -> !"OK".equals(r.state())).toList();
    }

    /**
     * A sentence when approving this pending expense would take its category or cost center over the monthly budget, otherwise null.
     * Counts what is already approved that month plus this expense.
     */
    @Transactional(readOnly = true)
    public String warningFor(Expense e) {
        if (e.getStatus() != ExpenseStatus.PENDING || e.getExpenseDate() == null) return null;
        YearMonth ym = YearMonth.from(e.getExpenseDate());
        List<ExpenseBudget> budgets = new ArrayList<>();
        repository.findByAccountIdAndBudgetYear(e.getAccount().getId(), ym.getYear()).ifPresent(budgets::add);
        if (e.getCostCenter() != null) repository.findByCostCenterIdAndBudgetYear(e.getCostCenter().getId(), ym.getYear()).ifPresent(budgets::add);
        if (budgets.isEmpty()) return null;
        List<Expense> approved = expenseRepository.findByStatusAndExpenseDateBetweenOrderByExpenseDateAsc(ExpenseStatus.APPROVED, ym.atDay(1), ym.atEndOfMonth());
        BigDecimal mine = net(e);
        for (ExpenseBudget b : budgets) {
            BigDecimal projected = sum(approved, b).add(mine);
            if (projected.compareTo(b.getMonthlyAmount()) > 0) {
                return "Over budget: " + label(b) + " has " + b.getMonthlyAmount().setScale(0, RoundingMode.HALF_UP).toPlainString() + " a month; "
                        + "this would bring " + ym + " to " + projected.setScale(0, RoundingMode.HALF_UP).toPlainString() + ".";
            }
        }
        return null;
    }

    private BudgetStatusRow row(ExpenseBudget b, BigDecimal spent, BigDecimal pending) {
        BigDecimal budget = b.getMonthlyAmount();
        BigDecimal remaining = budget.subtract(spent);
        double pct = budget.signum() == 0 ? 0 : spent.multiply(BigDecimal.valueOf(100)).divide(budget, 1, RoundingMode.HALF_UP).doubleValue();
        String state = pct > 100 ? "OVER" : pct >= 80 ? "WARNING" : "OK";
        return new BudgetStatusRow(b.getId(), label(b), b.getAccount() != null ? "CATEGORY" : "COST_CENTER", budget, spent, pending, remaining, pct, state);
    }

    private static BigDecimal sum(List<Expense> list, ExpenseBudget b) {
        BigDecimal total = BigDecimal.ZERO;
        for (Expense e : list) {
            boolean match = b.getAccount() != null
                    ? e.getAccount() != null && e.getAccount().getId().equals(b.getAccount().getId())
                    : e.getCostCenter() != null && e.getCostCenter().getId().equals(b.getCostCenter().getId());
            if (match) total = total.add(net(e));
        }
        return total;
    }

    static BigDecimal net(Expense e) {
        return e.getAmount().subtract(e.getTaxAmount() != null ? e.getTaxAmount() : BigDecimal.ZERO);
    }

    private static String label(ExpenseBudget b) {
        return b.getAccount() != null ? b.getAccount().getName() : b.getCostCenter().getName();
    }

    private static BudgetDto toDto(ExpenseBudget b) {
        Account a = b.getAccount();
        CostCenter c = b.getCostCenter();
        return new BudgetDto(b.getId(), a != null ? a.getId() : null, a != null ? a.getCode() : null, a != null ? a.getName() : null,
                c != null ? c.getId() : null, c != null ? c.getName() : null, b.getBudgetYear(), b.getMonthlyAmount(), b.getNote());
    }
}
