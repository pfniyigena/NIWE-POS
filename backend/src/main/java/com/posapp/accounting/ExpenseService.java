package com.posapp.accounting;

import com.posapp.alerts.AlertMailService;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchService;
import com.posapp.common.ApiException;
import com.posapp.accounting.dto.ExpenseDto;
import com.posapp.accounting.dto.ExpenseRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin &gt; Accounting &gt; Expenses — full lifecycle for {@link Expense} (Fourier: "Expense
 * Management Module" — approval workflow, edit/void/settle, receipts, and recurring expenses).
 * {@link #create} only ever logs a new expense as {@link ExpenseStatus#PENDING} — exactly the
 * same "log the claim, don't apply its effect yet" split {@code
 * com.posapp.inventory.StockAdjustmentService} already draws between {@code create} and {@code
 * confirm} — never touching {@link AccountingService} at all. Only {@link #approve} — moving the
 * status to {@link ExpenseStatus#APPROVED} — actually calls {@code
 * AccountingService#postExpense}; {@link #reject} discards a PENDING entry with no side effects;
 * {@link #voidExpense} reverses an already-posted APPROVED entry; {@link #settle} records that an
 * APPROVED-but-unpaid bill has since actually been paid off. See each method's own javadoc.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository repository;
    private final AccountRepository accountRepository;
    private final CostCenterService costCenterService;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;
    private final AlertMailService alertMailService;
    /** Validates the branch chosen on an expense. */
    private final BranchService branchService;
    /** Staff limited to certain branches can only approve/reject/void/settle those branches' expenses. */
    private final com.posapp.branch.BranchAccessService branchAccessService;

    public ExpenseService(ExpenseRepository repository, AccountRepository accountRepository,
                           CostCenterService costCenterService, AccountingService accountingService,
                           AuditLogService auditLogService, AlertMailService alertMailService,
                           BranchService branchService, com.posapp.branch.BranchAccessService branchAccessService) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.costCenterService = costCenterService;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
        this.alertMailService = alertMailService;
        this.branchService = branchService;
        this.branchAccessService = branchAccessService;
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> listAll() {
        return repository.findAllByOrderByExpenseDateDesc().stream().map(ExpenseDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ExpenseDto getById(Long id) {
        return ExpenseDto.from(getEntityOrThrow(id));
    }

    /** Plain create — see {@link #create(ExpenseRequest, Long)} for the recurring-template-tagged overload. */
    @Transactional
    public ExpenseDto create(ExpenseRequest request) {
        return create(request, null);
    }

    /**
     * Only ever logs this as {@link ExpenseStatus#PENDING} — never posts to Accounting (see
     * class javadoc). {@code recurringTemplateId} is non-null only when {@code
     * com.posapp.recurringexpense.RecurringExpenseTemplateService}'s scheduled sweep is the
     * caller — a manually-entered expense always passes null (via {@link #create(ExpenseRequest)}).
     */
    @Transactional
    public ExpenseDto create(ExpenseRequest request, Long recurringTemplateId) {
        Account account = resolveExpenseAccount(request.getAccountId());
        Expense expense = new Expense();
        applyRequest(expense, request, account);
        expense.setStatus(ExpenseStatus.PENDING);
        expense.setRecurringTemplateId(recurringTemplateId);
        Expense saved = repository.save(expense);
        auditLogService.record("EXPENSE_CREATED",
                "Logged expense of " + saved.getAmount() + " to " + account.getName() + " — " + saved.getPayeeName() + " (awaiting approval)",
                "Expense", saved.getId());
        alertMailService.alertExpenseAwaitingApproval(saved);
        return ExpenseDto.from(saved);
    }

    /** Only a PENDING expense can still be edited — mirrors SalesOrderService.updateDraft's "only DRAFT" stance. */
    @Transactional
    public ExpenseDto update(Long id, ExpenseRequest request) {
        Expense expense = getEntityOrThrow(id);
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw ApiException.badRequest("Only a pending expense can be edited — this one is " + expense.getStatus());
        }
        Account account = resolveExpenseAccount(request.getAccountId());
        applyRequest(expense, request, account);
        Expense saved = repository.save(expense);
        auditLogService.record("EXPENSE_UPDATED", "Updated expense " + saved.getId() + " — " + saved.getPayeeName(), "Expense", saved.getId());
        return ExpenseDto.from(saved);
    }

    /**
     * PENDING -&gt; APPROVED: the moment this expense actually posts to Accounting (see {@code
     * AccountingService#postExpense}) — enforced in {@code ExpenseController} as ADMIN or {@code
     * Permission.APPROVE_EXPENSE}, same maker-checker split {@code
     * StockAdjustmentService#confirm} already draws.
     */
    @Transactional
    public ExpenseDto approve(Long id) {
        Expense expense = getEntityOrThrow(id);
        branchAccessService.assertCanAccess(expense.getBranchId());
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw ApiException.badRequest("Only a pending expense can be approved — this one is " + expense.getStatus());
        }
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setApprovedAt(Instant.now());
        Expense saved = repository.save(expense);
        accountingService.postExpense(saved);
        auditLogService.record("EXPENSE_APPROVED",
                "Approved expense of " + saved.getAmount() + " to " + saved.getAccount().getName() + " — " + saved.getPayeeName(),
                "Expense", saved.getId());
        return ExpenseDto.from(saved);
    }

    /** PENDING -&gt; REJECTED: discards it with no Accounting impact at all — same enforcement as {@link #approve}. */
    @Transactional
    public ExpenseDto reject(Long id, String reason) {
        Expense expense = getEntityOrThrow(id);
        branchAccessService.assertCanAccess(expense.getBranchId());
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw ApiException.badRequest("Only a pending expense can be rejected — this one is " + expense.getStatus());
        }
        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setRejectedAt(Instant.now());
        expense.setRejectionReason(reason);
        Expense saved = repository.save(expense);
        auditLogService.record("EXPENSE_REJECTED", "Rejected expense " + saved.getId() + " — " + saved.getPayeeName(), "Expense", saved.getId());
        return ExpenseDto.from(saved);
    }

    /**
     * APPROVED -&gt; VOIDED: reverses an already-posted expense with an equal-and-opposite entry
     * (see {@code AccountingService#postExpenseVoid}) rather than deleting it — same stance as
     * every other posted-then-corrected document in this app. Refused once this bill has already
     * been {@link #settle settled} — the settlement itself would be left dangling against a
     * payable that no longer exists; correct via a fresh adjusting entry instead in that case.
     */
    @Transactional
    public ExpenseDto voidExpense(Long id, String reason) {
        Expense expense = getEntityOrThrow(id);
        branchAccessService.assertCanAccess(expense.getBranchId());
        if (expense.getStatus() != ExpenseStatus.APPROVED) {
            throw ApiException.badRequest("Only an approved expense can be voided — this one is " + expense.getStatus());
        }
        if (expense.getSettledAt() != null) {
            throw ApiException.badRequest("This bill has already been settled — void the settlement's effect with a fresh adjusting journal entry instead");
        }
        expense.setStatus(ExpenseStatus.VOIDED);
        expense.setVoidedAt(Instant.now());
        expense.setVoidReason(reason);
        Expense saved = repository.save(expense);
        accountingService.postExpenseVoid(saved);
        auditLogService.record("EXPENSE_VOIDED", "Voided expense " + saved.getId() + " — " + saved.getPayeeName(), "Expense", saved.getId());
        return ExpenseDto.from(saved);
    }

    /**
     * Records that an APPROVED, still-unpaid ({@code paid == false}) bill has since actually been
     * paid off — posts Cash against the Accounts Payable balance it created at approval time (see
     * {@code AccountingService#postExpenseSettlement}). Never changes {@code paid} itself (a
     * snapshot of what was true at approval time) and can only ever happen once per expense.
     */
    @Transactional
    public ExpenseDto settle(Long id, LocalDate settledDate) {
        Expense expense = getEntityOrThrow(id);
        branchAccessService.assertCanAccess(expense.getBranchId());
        if (expense.getStatus() != ExpenseStatus.APPROVED) {
            throw ApiException.badRequest("Only an approved expense can be settled — this one is " + expense.getStatus());
        }
        if (expense.isPaid()) {
            throw ApiException.badRequest("This expense already posted as paid — there is no outstanding bill to settle");
        }
        if (expense.getSettledAt() != null) {
            throw ApiException.badRequest("This bill was already settled");
        }
        LocalDate effectiveDate = settledDate != null ? settledDate : LocalDate.now();
        expense.setSettledAt(Instant.now());
        Expense saved = repository.save(expense);
        accountingService.postExpenseSettlement(saved, effectiveDate);
        auditLogService.record("EXPENSE_SETTLED", "Settled bill " + saved.getId() + " — " + saved.getPayeeName(), "Expense", saved.getId());
        return ExpenseDto.from(saved);
    }

    private Account resolveExpenseAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account not found: " + accountId));
        if (account.getType() != AccountType.EXPENSE) {
            throw ApiException.badRequest("'" + account.getName() + "' is not an Expense account — pick an Expense-type category");
        }
        return account;
    }

    private void applyRequest(Expense expense, ExpenseRequest request, Account account) {
        expense.setExpenseDate(request.getExpenseDate());
        expense.setAccount(account);
        expense.setPayeeType(request.getPayeeType());
        expense.setPayeeId(request.getPayeeId());
        expense.setPayeeName(request.getPayeeName());
        expense.setAmount(request.getAmount());
        expense.setPaid(request.isPaid());
        expense.setMemo(request.getMemo());
        expense.setReceiptDataUrl(request.getReceiptDataUrl());
        expense.setCostCenter(request.getCostCenterId() != null ? costCenterService.getEntityOrThrow(request.getCostCenterId()) : null);
        // Branch: an explicit choice wins; otherwise keep the existing one on an edit, head office on a new expense.
        if (request.getBranchId() != null || expense.getBranchId() == null) {
            expense.setBranchId(branchService.resolveForDocument(request.getBranchId()));
        }
    }

    private Expense getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Expense not found: " + id));
    }
}
