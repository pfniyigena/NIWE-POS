package com.posapp.accounting;

import com.posapp.i18n.I18n;
import com.posapp.accounting.dto.ExpenseDto;
import com.posapp.accounting.dto.ExpenseExtras.EventDto;
import com.posapp.accounting.dto.ExpenseExtras.PayRequest;
import com.posapp.accounting.dto.ExpenseExtras.PaymentAccountDto;
import com.posapp.accounting.dto.ExpenseExtras.PaymentDto;
import com.posapp.accounting.dto.ExpenseRequest;
import com.posapp.alerts.AlertMailService;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchService;
import com.posapp.common.ApiException;
import com.posapp.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Admin &gt; Accounting &gt; Expenses — full lifecycle for {@link Expense}.
 * {@link #create} only ever logs a new expense as {@link ExpenseStatus#PENDING} and never touches {@link AccountingService}.
 * Only {@link #approve} posts it; {@link #reject} discards a pending one; {@link #voidExpense} reverses an approved one that has had no payment;
 * {@link #pay} / {@link #settle} record money paid against an approved unpaid bill.
 *
 * <p>{@code amount} is always the full total. {@code taxAmount} is the part of it that is recoverable input VAT: it posts to its own
 * account instead of the expense category, so the category only carries the net cost.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository repository;
    private final AccountRepository accountRepository;
    private final CostCenterService costCenterService;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;
    private final AlertMailService alertMailService;
    private final BranchService branchService;
    private final com.posapp.branch.BranchAccessService branchAccessService;
    private final ExpensePaymentRepository paymentRepository;
    private final ExpenseAttachmentRepository attachmentRepository;
    private final ExpenseEventRepository eventRepository;
    private final ExpenseSettingsService settingsService;
    private final ExpenseBudgetService budgetService;

    public ExpenseService(ExpenseRepository repository, AccountRepository accountRepository,
                          CostCenterService costCenterService, AccountingService accountingService,
                          AuditLogService auditLogService, AlertMailService alertMailService,
                          BranchService branchService, com.posapp.branch.BranchAccessService branchAccessService,
                          ExpensePaymentRepository paymentRepository, ExpenseAttachmentRepository attachmentRepository,
                          ExpenseEventRepository eventRepository, ExpenseSettingsService settingsService, ExpenseBudgetService budgetService) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.costCenterService = costCenterService;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
        this.alertMailService = alertMailService;
        this.branchService = branchService;
        this.branchAccessService = branchAccessService;
        this.paymentRepository = paymentRepository;
        this.attachmentRepository = attachmentRepository;
        this.eventRepository = eventRepository;
        this.settingsService = settingsService;
        this.budgetService = budgetService;
    }

    /** What the list screen can filter on. Every field is optional. */
    public record Filter(String status, Long accountId, Long costCenterId, String q, LocalDate from, LocalDate to,
                         BigDecimal minAmount, BigDecimal maxAmount, Boolean paid, String view, Long branchId) {
    }

    private record Actor(Long id, String name, boolean admin, boolean largeApprover) {
    }

    private static Actor actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p && p.getUser() != null) {
            boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            boolean large = admin || auth.getAuthorities().stream().anyMatch(a -> "PERM_APPROVE_LARGE_EXPENSE".equals(a.getAuthority()));
            return new Actor(p.getUser().getId(), p.getUser().getFullName(), admin, large);
        }
        return new Actor(null, "System", false, false);
    }

    public static String currentActorName() {
        return actor().name();
    }

    // ---------------------------------------------------------------- reading

    @Transactional(readOnly = true)
    public List<ExpenseDto> listAll() {
        return list(null);
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> list(Filter f) {
        int dueSoon = settingsService.entity().getDueSoonDays();
        Map<Long, Integer> files = fileCounts();
        Long me = actor().id();
        String q = f != null && f.q() != null && !f.q().isBlank() ? f.q().trim().toLowerCase(Locale.ROOT) : null;
        return repository.findAllByOrderByExpenseDateDesc().stream()
                .map(e -> ExpenseDto.from(e, dueSoon).withExtras(files.getOrDefault(e.getId(), 0), null))
                .filter(d -> f == null || matches(d, f, q, me))
                .toList();
    }

    private static boolean matches(ExpenseDto d, Filter f, String q, Long me) {
        if (f.status() != null && !f.status().isBlank() && !d.status().name().equalsIgnoreCase(f.status())) return false;
        if (f.accountId() != null && !f.accountId().equals(d.accountId())) return false;
        if (f.costCenterId() != null && !f.costCenterId().equals(d.costCenterId())) return false;
        if (f.branchId() != null && !f.branchId().equals(d.branchId())) return false;
        if (f.from() != null && d.expenseDate().isBefore(f.from())) return false;
        if (f.to() != null && d.expenseDate().isAfter(f.to())) return false;
        if (f.minAmount() != null && d.amount().compareTo(f.minAmount()) < 0) return false;
        if (f.maxAmount() != null && d.amount().compareTo(f.maxAmount()) > 0) return false;
        if (f.paid() != null && f.paid() != d.paid()) return false;
        if (f.view() != null && !f.view().isBlank()) {
            boolean ok = switch (f.view().toLowerCase(Locale.ROOT)) {
                case "pending" -> d.status() == ExpenseStatus.PENDING;
                case "unpaid" -> d.status() == ExpenseStatus.APPROVED && !d.paid() && d.balance().signum() > 0;
                case "overdue" -> "OVERDUE".equals(d.dueState());
                case "due-soon" -> "DUE_SOON".equals(d.dueState());
                case "mine" -> me != null && me.equals(d.createdByUserId());
                default -> true;
            };
            if (!ok) return false;
        }
        if (q != null) {
            String hay = (nz(d.payeeName()) + " " + nz(d.memo()) + " " + nz(d.expenseNumber()) + " " + nz(d.reference()) + " " + nz(d.accountName())).toLowerCase(Locale.ROOT);
            if (!hay.contains(q)) return false;
        }
        return true;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private Map<Long, Integer> fileCounts() {
        Map<Long, Integer> m = new HashMap<>();
        for (Object[] row : attachmentRepository.countsByExpense()) {
            m.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return m;
    }

    @Transactional(readOnly = true)
    public ExpenseDto getById(Long id) {
        return toDto(getEntityOrThrow(id));
    }

    private ExpenseDto toDto(Expense e) {
        return ExpenseDto.from(e, settingsService.entity().getDueSoonDays())
                .withExtras((int) attachmentRepository.countByExpenseId(e.getId()), budgetService.warningFor(e));
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> payments(Long id) {
        getEntityOrThrow(id);
        return paymentRepository.findByExpenseIdOrderByPaidOnAscIdAsc(id).stream().map(p -> new PaymentDto(p.getId(), p.getAmount(), p.getPaidOn(),
                p.getPaidFromAccount() != null ? p.getPaidFromAccount().getId() : null,
                p.getPaidFromAccount() != null ? p.getPaidFromAccount().getName() : I18n.t("Cash"),
                p.getNote(), p.getCreatedByName(), p.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<EventDto> events(Long id) {
        getEntityOrThrow(id);
        return eventRepository.findByExpenseIdOrderByIdDesc(id).stream()
                .map(e -> new EventDto(e.getId(), e.getKind(), e.getDetail(), e.getCreatedByName(), e.getCreatedAt())).toList();
    }

    /** Accounts money can be paid from: Cash and any bank, mobile money or other asset account added to the chart. */
    @Transactional(readOnly = true)
    public List<PaymentAccountDto> paymentAccounts() {
        return accountRepository.findByActiveTrueAndTypeOrderByCodeAsc(AccountType.ASSET).stream()
                .filter(ExpenseService::isPaymentAccount)
                .map(a -> new PaymentAccountDto(a.getId(), a.getCode(), a.getName(), AccountService.CASH.equals(a.getWellKnownKey()))).toList();
    }

    private static boolean isPaymentAccount(Account a) {
        return a.getType() == AccountType.ASSET && a.isActive() && (a.getWellKnownKey() == null || AccountService.CASH.equals(a.getWellKnownKey()));
    }

    // ---------------------------------------------------------------- writing

    /** Plain create — see {@link #create(ExpenseRequest, Long)} for the recurring-template-tagged overload. */
    @Transactional
    public ExpenseDto create(ExpenseRequest request) {
        return create(request, null);
    }

    /** Only ever logs this as PENDING; {@code recurringTemplateId} is set only by the recurring-expense sweep. */
    @Transactional
    public ExpenseDto create(ExpenseRequest request, Long recurringTemplateId) {
        Account account = resolveExpenseAccount(request.getAccountId());
        Expense expense = new Expense();
        applyRequest(expense, request, account);
        expense.setStatus(ExpenseStatus.PENDING);
        expense.setRecurringTemplateId(recurringTemplateId);
        Actor who = actor();
        if (recurringTemplateId == null) {
            expense.setCreatedByUserId(who.id());
            expense.setCreatedByName(who.name());
        } else {
            expense.setCreatedByName("Recurring expense");
        }
        expense.setExpenseNumber("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        Expense saved = repository.save(expense);
        saved.setExpenseNumber(String.format("EXP-%06d", saved.getId()));
        saved = repository.save(saved);
        event(saved.getId(), "CREATED", "Logged " + saved.getAmount() + " to " + account.getName() + " for " + saved.getPayeeName() + " (awaiting approval)", expense.getCreatedByName());
        auditLogService.record("EXPENSE_CREATED",
                "Logged expense " + saved.getExpenseNumber() + " of " + saved.getAmount() + " to " + account.getName() + " — " + saved.getPayeeName() + " (awaiting approval)",
                "Expense", saved.getId());
        alertMailService.alertExpenseAwaitingApproval(saved);
        return toDto(saved);
    }

    /** Only a PENDING expense can still be edited. */
    @Transactional
    public ExpenseDto update(Long id, ExpenseRequest request) {
        Expense expense = getEntityOrThrow(id);
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw ApiException.badRequest(I18n.t("Only a pending expense can be edited — this one is ") + expense.getStatus());
        }
        Account account = resolveExpenseAccount(request.getAccountId());
        BigDecimal before = expense.getAmount();
        applyRequest(expense, request, account);
        Expense saved = repository.save(expense);
        event(saved.getId(), "EDITED", "Edited" + (before.compareTo(saved.getAmount()) != 0 ? " (amount " + before + " to " + saved.getAmount() + ")" : ""));
        auditLogService.record("EXPENSE_UPDATED", "Updated expense " + saved.getExpenseNumber() + " — " + saved.getPayeeName(), "Expense", saved.getId());
        return toDto(saved);
    }

    /** Only a pending or rejected expense can be deleted: nothing was ever posted for it. */
    @Transactional
    public void delete(Long id) {
        Expense expense = getEntityOrThrow(id);
        branchAccessService.assertCanAccess(expense.getBranchId());
        if (expense.getStatus() != ExpenseStatus.PENDING && expense.getStatus() != ExpenseStatus.REJECTED) {
            throw ApiException.badRequest(I18n.t("Only a pending or rejected expense can be deleted. This one is ") + expense.getStatus() + I18n.t(". An approved expense can be voided instead."));
        }
        attachmentRepository.deleteByExpenseId(id);
        eventRepository.deleteByExpenseId(id);
        repository.delete(expense);
        auditLogService.record("EXPENSE_DELETED", "Deleted " + expense.getStatus().name().toLowerCase(Locale.ROOT) + " expense " + expense.getExpenseNumber() + " — " + expense.getPayeeName()
                + " (" + expense.getAmount() + ")", "Expense", id);
    }

    /**
     * PENDING -&gt; APPROVED: posts to Accounting. Refused when the signed-in user entered the expense and self-approval is blocked,
     * when the amount is above the approval limit and the user is neither an administrator nor allowed large expenses,
     * and (when switched on) when it would take its category or cost center over the monthly budget unless an administrator overrides.
     */
    @Transactional
    public ExpenseDto approve(Long id) {
        return approve(id, false);
    }

    @Transactional
    public ExpenseDto approve(Long id, boolean overrideBudget) {
        Expense expense = getEntityOrThrow(id);
        branchAccessService.assertCanAccess(expense.getBranchId());
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw ApiException.badRequest(I18n.t("Only a pending expense can be approved — this one is ") + expense.getStatus());
        }
        Actor who = actor();
        ExpenseSettings s = settingsService.entity();
        assertNotSelf(expense, who, s, "approve");
        BigDecimal limit = s.getApprovalLimit() != null ? s.getApprovalLimit() : BigDecimal.ZERO;
        if (limit.signum() > 0 && expense.getAmount().compareTo(limit) > 0 && !who.largeApprover()) {
            throw ApiException.forbidden(I18n.t("This expense is above the approval limit of ") + limit.stripTrailingZeros().toPlainString()
                    + I18n.t(". An administrator or someone allowed to approve large expenses must approve it."));
        }
        String warning = budgetService.warningFor(expense);
        boolean overBudgetOverride = false;
        if (warning != null && s.isBlockOverBudget()) {
            if (overrideBudget && who.admin()) {
                overBudgetOverride = true;
            } else {
                throw ApiException.badRequest(warning + (who.admin() ? I18n.t(" Approve with the budget override to go ahead.") : I18n.t(" Ask an administrator to approve it.")));
            }
        }
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setApprovedAt(Instant.now());
        expense.setApprovedByUserId(who.id());
        expense.setApprovedByName(who.name());
        if (!expense.isPaid() && expense.getDueDate() == null) {
            expense.setDueDate(expense.getExpenseDate().plusDays(s.getDefaultDueDays()));
        }
        Expense saved = repository.save(expense);
        accountingService.postExpense(saved);
        event(saved.getId(), "APPROVED", "Approved" + (overBudgetOverride ? " over budget (administrator override)" : ""));
        auditLogService.record("EXPENSE_APPROVED",
                "Approved expense " + saved.getExpenseNumber() + " of " + saved.getAmount() + " to " + saved.getAccount().getName() + " — " + saved.getPayeeName()
                        + (overBudgetOverride ? " (over budget, override)" : ""),
                "Expense", saved.getId());
        return toDto(saved);
    }

    /** PENDING -&gt; REJECTED: discards it with no Accounting impact at all. */
    @Transactional
    public ExpenseDto reject(Long id, String reason) {
        Expense expense = getEntityOrThrow(id);
        branchAccessService.assertCanAccess(expense.getBranchId());
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw ApiException.badRequest(I18n.t("Only a pending expense can be rejected — this one is ") + expense.getStatus());
        }
        Actor who = actor();
        assertNotSelf(expense, who, settingsService.entity(), "reject");
        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setRejectedAt(Instant.now());
        expense.setRejectionReason(reason);
        expense.setRejectedByName(who.name());
        Expense saved = repository.save(expense);
        event(saved.getId(), "REJECTED", "Rejected" + (reason != null && !reason.isBlank() ? ": " + reason : ""));
        auditLogService.record("EXPENSE_REJECTED", "Rejected expense " + saved.getExpenseNumber() + " — " + saved.getPayeeName(), "Expense", saved.getId());
        return toDto(saved);
    }

    /**
     * APPROVED -&gt; VOIDED: reverses an already-posted expense. Refused once any payment was made on the bill, because the
     * payments would be left against a payable that no longer exists.
     */
    @Transactional
    public ExpenseDto voidExpense(Long id, String reason) {
        Expense expense = getEntityOrThrow(id);
        branchAccessService.assertCanAccess(expense.getBranchId());
        if (expense.getStatus() != ExpenseStatus.APPROVED) {
            throw ApiException.badRequest(I18n.t("Only an approved expense can be voided — this one is ") + expense.getStatus());
        }
        if (expense.getSettledAt() != null || (!expense.isPaid() && expense.getAmountPaid().signum() > 0)) {
            throw ApiException.badRequest(I18n.t("A payment has already been made on this bill — correct it with a fresh adjusting journal entry instead"));
        }
        Actor who = actor();
        assertNotSelf(expense, who, settingsService.entity(), "void");
        expense.setStatus(ExpenseStatus.VOIDED);
        expense.setVoidedAt(Instant.now());
        expense.setVoidReason(reason);
        expense.setVoidedByName(who.name());
        Expense saved = repository.save(expense);
        accountingService.postExpenseVoid(saved);
        event(saved.getId(), "VOIDED", "Voided" + (reason != null && !reason.isBlank() ? ": " + reason : ""));
        auditLogService.record("EXPENSE_VOIDED", "Voided expense " + saved.getExpenseNumber() + " — " + saved.getPayeeName(), "Expense", saved.getId());
        return toDto(saved);
    }

    /** Pays the rest of an approved unpaid bill in one go (from the bill's chosen account, or Cash). */
    @Transactional
    public ExpenseDto settle(Long id, LocalDate settledDate) {
        Expense expense = getEntityOrThrow(id);
        if (expense.getStatus() == ExpenseStatus.APPROVED && !expense.isPaid() && expense.getSettledAt() != null) {
            throw ApiException.badRequest(I18n.t("This bill was already settled"));
        }
        PayRequest r = new PayRequest();
        r.setAmount(expense.getAmount().subtract(expense.getAmountPaid()));
        r.setPaidOn(settledDate);
        r.setPaidFromAccountId(expense.getPaidFromAccount() != null ? expense.getPaidFromAccount().getId() : null);
        return pay(id, r);
    }

    /** Records a full or part payment on an approved unpaid bill. */
    @Transactional
    public ExpenseDto pay(Long id, PayRequest r) {
        Expense expense = getEntityOrThrow(id);
        branchAccessService.assertCanAccess(expense.getBranchId());
        if (expense.getStatus() != ExpenseStatus.APPROVED) {
            throw ApiException.badRequest(I18n.t("Only an approved expense can be paid — this one is ") + expense.getStatus());
        }
        if (expense.isPaid()) {
            throw ApiException.badRequest(I18n.t("This expense already posted as paid — there is no outstanding bill to pay"));
        }
        BigDecimal balance = expense.getAmount().subtract(expense.getAmountPaid());
        if (balance.signum() <= 0 || expense.getSettledAt() != null) {
            throw ApiException.badRequest(I18n.t("This bill is already fully paid"));
        }
        BigDecimal amount = r.getAmount() != null ? r.getAmount() : balance;
        if (amount.signum() <= 0) throw ApiException.badRequest(I18n.t("The payment must be more than zero."));
        if (amount.compareTo(balance) > 0) {
            throw ApiException.badRequest(I18n.t("The payment is more than the ") + balance.stripTrailingZeros().toPlainString() + I18n.t(" still owed on this bill."));
        }
        LocalDate date = r.getPaidOn() != null ? r.getPaidOn() : LocalDate.now();
        if (date.isBefore(expense.getExpenseDate())) {
            throw ApiException.badRequest(I18n.t("A payment cannot be dated before the bill."));
        }
        Account from = r.getPaidFromAccountId() != null ? resolvePaymentAccount(r.getPaidFromAccountId()) : null;
        Actor who = actor();
        ExpensePayment p = new ExpensePayment();
        p.setExpenseId(expense.getId());
        p.setAmount(amount);
        p.setPaidOn(date);
        p.setPaidFromAccount(from);
        p.setNote(r.getNote() == null || r.getNote().isBlank() ? null : r.getNote().trim());
        p.setCreatedByName(who.name());
        paymentRepository.save(p);
        expense.setAmountPaid(expense.getAmountPaid().add(amount));
        boolean full = expense.getAmountPaid().compareTo(expense.getAmount()) >= 0;
        if (full) expense.setSettledAt(Instant.now());
        Expense saved = repository.save(expense);
        accountingService.postExpensePayment(saved, amount, date, from);
        event(saved.getId(), "PAYMENT", "Paid " + amount.stripTrailingZeros().toPlainString() + " from " + (from != null ? from.getName() : "Cash")
                + (full ? " — bill fully paid" : "; " + saved.getAmount().subtract(saved.getAmountPaid()).stripTrailingZeros().toPlainString() + " still owed"));
        auditLogService.record(full ? "EXPENSE_SETTLED" : "EXPENSE_PART_PAID",
                (full ? "Settled bill " : "Part-paid bill ") + saved.getExpenseNumber() + " — " + saved.getPayeeName() + " (" + amount + ")", "Expense", saved.getId());
        return toDto(saved);
    }

    // ---------------------------------------------------------------- helpers

    private void assertNotSelf(Expense e, Actor who, ExpenseSettings s, String verb) {
        if (s.isBlockSelfApproval() && who.id() != null && who.id().equals(e.getCreatedByUserId())) {
            throw ApiException.forbidden(I18n.t("You entered this expense yourself, so someone else has to ") + verb + " it.");
        }
    }

    @Transactional
    public void event(Long expenseId, String kind, String detail) {
        event(expenseId, kind, detail, actor().name());
    }

    @Transactional
    public void event(Long expenseId, String kind, String detail, String by) {
        ExpenseEvent ev = new ExpenseEvent();
        ev.setExpenseId(expenseId);
        ev.setKind(kind);
        ev.setDetail(detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail);
        ev.setCreatedByName(by);
        eventRepository.save(ev);
    }

    private Account resolveExpenseAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound(I18n.t("Account not found: ") + accountId));
        if (account.getType() != AccountType.EXPENSE) {
            throw ApiException.badRequest("'" + account.getName() + I18n.t("' is not an Expense account — pick an Expense-type category"));
        }
        return account;
    }

    private Account resolvePaymentAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound(I18n.t("Account not found: ") + accountId));
        if (!isPaymentAccount(account)) {
            throw ApiException.badRequest("'" + account.getName() + I18n.t("' cannot be used to pay from. Choose Cash, a bank or a mobile money account."));
        }
        return account;
    }

    private void applyRequest(Expense expense, ExpenseRequest request, Account account) {
        BigDecimal tax = request.getTaxAmount() != null ? request.getTaxAmount() : BigDecimal.ZERO;
        if (tax.signum() < 0) throw ApiException.badRequest(I18n.t("The VAT amount cannot be negative."));
        if (tax.signum() > 0 && tax.compareTo(request.getAmount()) >= 0) {
            throw ApiException.badRequest(I18n.t("The VAT amount must be less than the total amount (the total includes the VAT)."));
        }
        if (request.getDueDate() != null && request.getDueDate().isBefore(request.getExpenseDate())) {
            throw ApiException.badRequest(I18n.t("The due date cannot be before the expense date."));
        }
        Account from = request.getPaidFromAccountId() != null ? resolvePaymentAccount(request.getPaidFromAccountId()) : null;
        expense.setExpenseDate(request.getExpenseDate());
        expense.setAccount(account);
        expense.setPayeeType(request.getPayeeType());
        expense.setPayeeId(request.getPayeeId());
        expense.setPayeeName(request.getPayeeName());
        expense.setAmount(request.getAmount());
        expense.setTaxAmount(tax);
        expense.setPaid(request.isPaid());
        expense.setMemo(request.getMemo());
        expense.setReference(request.getReference() == null || request.getReference().isBlank() ? null : request.getReference().trim());
        expense.setReceiptDataUrl(request.getReceiptDataUrl());
        expense.setPaidFromAccount(from);
        if (request.isPaid()) {
            expense.setDueDate(null);
        } else if (request.getDueDate() != null) {
            expense.setDueDate(request.getDueDate());
        } else {
            expense.setDueDate(request.getExpenseDate().plusDays(settingsService.entity().getDefaultDueDays()));
        }
        expense.setCostCenter(request.getCostCenterId() != null ? costCenterService.getEntityOrThrow(request.getCostCenterId()) : null);
        if (request.getBranchId() != null || expense.getBranchId() == null) {
            expense.setBranchId(branchService.resolveForDocument(request.getBranchId()));
        }
    }

    Expense getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(I18n.t("Expense not found: ") + id));
    }
}
