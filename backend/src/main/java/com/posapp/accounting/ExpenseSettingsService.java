package com.posapp.accounting;

import com.posapp.accounting.dto.ExpenseExtras.SettingsDto;
import com.posapp.accounting.dto.ExpenseExtras.SettingsRequest;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** The one row of approval and bill rules for Expenses. */
@Service
public class ExpenseSettingsService {

    private final ExpenseSettingsRepository repository;
    private final AuditLogService auditLogService;

    public ExpenseSettingsService(ExpenseSettingsRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ExpenseSettings entity() {
        return repository.findAll().stream().findFirst().orElseGet(() -> repository.save(new ExpenseSettings()));
    }

    @Transactional
    public SettingsDto get() {
        return toDto(entity());
    }

    @Transactional
    public SettingsDto update(SettingsRequest r) {
        ExpenseSettings s = entity();
        if (r.getApprovalLimit() != null) {
            if (r.getApprovalLimit().signum() < 0) throw ApiException.badRequest("The approval limit cannot be negative (use 0 for no limit).");
            s.setApprovalLimit(r.getApprovalLimit());
        }
        if (r.getBlockSelfApproval() != null) s.setBlockSelfApproval(r.getBlockSelfApproval());
        if (r.getBlockOverBudget() != null) s.setBlockOverBudget(r.getBlockOverBudget());
        if (r.getDefaultDueDays() != null) {
            if (r.getDefaultDueDays() < 0 || r.getDefaultDueDays() > 365) throw ApiException.badRequest("Default due days must be between 0 and 365.");
            s.setDefaultDueDays(r.getDefaultDueDays());
        }
        if (r.getDueSoonDays() != null) {
            if (r.getDueSoonDays() < 0 || r.getDueSoonDays() > 90) throw ApiException.badRequest("Due soon days must be between 0 and 90.");
            s.setDueSoonDays(r.getDueSoonDays());
        }
        repository.save(s);
        auditLogService.record("EXPENSE_SETTINGS_UPDATED",
                "Expense rules: approval limit " + s.getApprovalLimit() + ", self-approval blocked " + s.isBlockSelfApproval()
                        + ", over-budget blocked " + s.isBlockOverBudget() + ", due " + s.getDefaultDueDays() + " days",
                "ExpenseSettings", s.getId());
        return toDto(s);
    }

    static SettingsDto toDto(ExpenseSettings s) {
        BigDecimal limit = s.getApprovalLimit() != null ? s.getApprovalLimit() : BigDecimal.ZERO;
        return new SettingsDto(limit, s.isBlockSelfApproval(), s.isBlockOverBudget(), s.getDefaultDueDays(), s.getDueSoonDays());
    }
}
