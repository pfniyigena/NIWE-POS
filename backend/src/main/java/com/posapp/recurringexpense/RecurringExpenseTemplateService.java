package com.posapp.recurringexpense;

import com.posapp.accounting.Account;
import com.posapp.accounting.AccountRepository;
import com.posapp.accounting.AccountType;
import com.posapp.accounting.CostCenter;
import com.posapp.accounting.CostCenterRepository;
import com.posapp.accounting.ExpenseService;
import com.posapp.accounting.dto.ExpenseRequest;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.recurringexpense.dto.RecurringExpenseTemplateDto;
import com.posapp.recurringexpense.dto.RecurringExpenseTemplateRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin &gt; Accounting &gt; Recurring expenses (Fourier: "Recurring expenses"). CRUD for {@link
 * RecurringExpenseTemplate} plus {@link #generateDueTemplates} — the daily scheduled sweep that
 * turns a due template into a brand-new PENDING {@code Expense} via {@code
 * ExpenseService#create(ExpenseRequest, Long)}, reusing that exact same creation path (and so the
 * exact same approval workflow) a manually-entered expense goes through — nothing here ever
 * posts to Accounting on its own. {@link #runNow} exposes the same single-template logic for an
 * admin who doesn't want to wait for the next scheduled sweep (e.g. just created a template and
 * wants to see what it produces right away).
 */
@Service
public class RecurringExpenseTemplateService {

    private final RecurringExpenseTemplateRepository repository;
    private final AccountRepository accountRepository;
    private final CostCenterRepository costCenterRepository;
    private final ExpenseService expenseService;
    private final AuditLogService auditLogService;

    public RecurringExpenseTemplateService(RecurringExpenseTemplateRepository repository,
                                            AccountRepository accountRepository,
                                            CostCenterRepository costCenterRepository,
                                            ExpenseService expenseService,
                                            AuditLogService auditLogService) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.costCenterRepository = costCenterRepository;
        this.expenseService = expenseService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseTemplateDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream().map(RecurringExpenseTemplateDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RecurringExpenseTemplateDto getById(Long id) {
        return RecurringExpenseTemplateDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public RecurringExpenseTemplateDto create(RecurringExpenseTemplateRequest request) {
        RecurringExpenseTemplate template = new RecurringExpenseTemplate();
        applyRequest(template, request);
        RecurringExpenseTemplate saved = repository.save(template);
        auditLogService.record("RECURRING_EXPENSE_TEMPLATE_CREATED",
                "Created recurring expense template '" + saved.getName() + "' (" + saved.getFrequency() + ", next " + saved.getNextRunDate() + ")",
                "RecurringExpenseTemplate", saved.getId());
        return RecurringExpenseTemplateDto.from(saved);
    }

    /** Freely editable at any time — this is just a template/configuration, not a posted document with a lifecycle of its own. */
    @Transactional
    public RecurringExpenseTemplateDto update(Long id, RecurringExpenseTemplateRequest request) {
        RecurringExpenseTemplate template = getEntityOrThrow(id);
        applyRequest(template, request);
        RecurringExpenseTemplate saved = repository.save(template);
        auditLogService.record("RECURRING_EXPENSE_TEMPLATE_UPDATED", "Updated recurring expense template '" + saved.getName() + "'", "RecurringExpenseTemplate", saved.getId());
        return RecurringExpenseTemplateDto.from(saved);
    }

    /** Pauses or resumes a template without losing its configuration or history — see class javadoc on "toggle, don't delete". */
    @Transactional
    public RecurringExpenseTemplateDto setActive(Long id, boolean active) {
        RecurringExpenseTemplate template = getEntityOrThrow(id);
        template.setActive(active);
        RecurringExpenseTemplate saved = repository.save(template);
        auditLogService.record(active ? "RECURRING_EXPENSE_TEMPLATE_RESUMED" : "RECURRING_EXPENSE_TEMPLATE_PAUSED",
                (active ? "Resumed" : "Paused") + " recurring expense template '" + saved.getName() + "'", "RecurringExpenseTemplate", saved.getId());
        return RecurringExpenseTemplateDto.from(saved);
    }

    /** Generates this one template's next expense immediately, regardless of whether {@code nextRunDate} has actually arrived yet — same effect as the scheduled sweep picking it up, just on demand. */
    @Transactional
    public RecurringExpenseTemplateDto runNow(Long id) {
        RecurringExpenseTemplate template = getEntityOrThrow(id);
        if (!template.isActive()) {
            throw ApiException.badRequest("This template is paused — resume it before running it");
        }
        generateFrom(template);
        return RecurringExpenseTemplateDto.from(template);
    }

    /**
     * Fourier: "Recurring expenses" — once a day, every still-{@code active} template whose
     * {@code nextRunDate} has arrived (or passed) generates its next PENDING {@code Expense} and
     * advances to its following run date. Each template is its own independent unit of work, so
     * one failing never stops the rest — mirrors {@code QuotationService}'s own daily sweep's
     * per-item isolation.
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void generateDueTemplates() {
        LocalDate today = LocalDate.now();
        for (RecurringExpenseTemplate template : repository.findByActiveTrueAndNextRunDateLessThanEqual(today)) {
            try {
                generateFrom(template);
            } catch (Exception ex) {
                auditLogService.record("RECURRING_EXPENSE_TEMPLATE_FAILED",
                        "Failed to generate the next expense for recurring template '" + template.getName() + "': " + ex.getMessage(),
                        "RecurringExpenseTemplate", template.getId());
            }
        }
    }

    private void generateFrom(RecurringExpenseTemplate template) {
        ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(template.getNextRunDate());
        request.setAccountId(template.getAccount().getId());
        request.setPayeeType(template.getPayeeType());
        request.setPayeeId(template.getPayeeId());
        request.setPayeeName(template.getPayeeName());
        request.setAmount(template.getAmount());
        request.setPaid(template.isPaid());
        request.setMemo(template.getMemo() != null ? template.getMemo() : ("Generated from recurring template: " + template.getName()));
        request.setCostCenterId(template.getCostCenter() != null ? template.getCostCenter().getId() : null);

        var generated = expenseService.create(request, template.getId());

        template.setLastRunAt(Instant.now());
        template.setNextRunDate(advance(template.getNextRunDate(), template.getFrequency()));
        repository.save(template);

        auditLogService.record("RECURRING_EXPENSE_TEMPLATE_RAN",
                "Generated expense #" + generated.id() + " of " + generated.amount() + " from recurring template '" + template.getName()
                        + "' (next due " + template.getNextRunDate() + ")",
                "RecurringExpenseTemplate", template.getId());
        // Fourier: "draft for review" — the new expense itself is what needs a human's attention,
        // not this template, so the one email that actually fires is ExpenseService.create's own
        // alertExpenseAwaitingApproval (above) — no separate "a template ran" alert on top of it.
    }

    private LocalDate advance(LocalDate current, RecurrenceFrequency frequency) {
        return frequency == RecurrenceFrequency.WEEKLY ? current.plusWeeks(1) : current.plusMonths(1);
    }

    private void applyRequest(RecurringExpenseTemplate template, RecurringExpenseTemplateRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> ApiException.notFound("Account not found: " + request.getAccountId()));
        if (account.getType() != AccountType.EXPENSE) {
            throw ApiException.badRequest("'" + account.getName() + "' is not an Expense account — pick an Expense-type category");
        }
        CostCenter costCenter = null;
        if (request.getCostCenterId() != null) {
            costCenter = costCenterRepository.findById(request.getCostCenterId())
                    .orElseThrow(() -> ApiException.notFound("Cost center not found: " + request.getCostCenterId()));
        }
        template.setName(request.getName());
        template.setAccount(account);
        template.setPayeeType(request.getPayeeType());
        template.setPayeeId(request.getPayeeId());
        template.setPayeeName(request.getPayeeName());
        template.setAmount(request.getAmount());
        template.setPaid(request.isPaid());
        template.setMemo(request.getMemo());
        template.setCostCenter(costCenter);
        template.setFrequency(request.getFrequency());
        template.setNextRunDate(request.getNextRunDate());
        template.setActive(request.isActive());
    }

    private RecurringExpenseTemplate getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Recurring expense template not found: " + id));
    }
}
