package com.posapp.accounting;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.accounting.dto.IncomeDto;
import com.posapp.accounting.dto.IncomeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin &gt; Accounting &gt; Incomes — {@link ExpenseService}'s mirror image for {@link Income}. */
@Service
public class IncomeService {

    private final IncomeRepository repository;
    private final AccountRepository accountRepository;
    private final CostCenterService costCenterService;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;
    /** Validates the branch chosen on an income. */
    private final com.posapp.branch.BranchService branchService;

    public IncomeService(IncomeRepository repository, AccountRepository accountRepository,
                          CostCenterService costCenterService, AccountingService accountingService,
                          AuditLogService auditLogService, com.posapp.branch.BranchService branchService) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.costCenterService = costCenterService;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
        this.branchService = branchService;
    }

    @Transactional(readOnly = true)
    public List<IncomeDto> listAll() {
        return repository.findAllByOrderByIncomeDateDesc().stream().map(IncomeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public IncomeDto getById(Long id) {
        return IncomeDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public IncomeDto create(IncomeRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> ApiException.notFound("Account not found: " + request.getAccountId()));
        if (account.getType() != AccountType.REVENUE) {
            throw ApiException.badRequest("'" + account.getName() + "' is not a Revenue account — pick a Revenue-type category");
        }
        Income income = new Income();
        income.setIncomeDate(request.getIncomeDate());
        income.setAccount(account);
        income.setPayeeType(request.getPayeeType());
        income.setPayeeId(request.getPayeeId());
        income.setPayeeName(request.getPayeeName());
        income.setAmount(request.getAmount());
        income.setReceived(request.isReceived());
        income.setMemo(request.getMemo());
        income.setBranchId(branchService.resolveForDocument(request.getBranchId()));
        if (request.getCostCenterId() != null) {
            income.setCostCenter(costCenterService.getEntityOrThrow(request.getCostCenterId()));
        }
        Income saved = repository.save(income);
        accountingService.postIncome(saved);
        auditLogService.record("INCOME_RECORDED", "Recorded income of " + saved.getAmount() + " to " + account.getName() + " — " + saved.getPayeeName(), "Income", saved.getId());
        return IncomeDto.from(saved);
    }

    private Income getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Income not found: " + id));
    }
}
