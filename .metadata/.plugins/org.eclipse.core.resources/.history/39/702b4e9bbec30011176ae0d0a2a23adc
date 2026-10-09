package com.posapp.accounting;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.accounting.dto.CostCenterDto;
import com.posapp.accounting.dto.CostCenterRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for {@link CostCenter} — Admin &gt; Accounting &gt; Cost Centers. Deliberately no
 * "delete" (same convention as {@link AccountService}'s own Chart of Accounts): a cost center
 * already referenced by a past {@link JournalEntryLine} must keep existing, so retiring one is
 * always a deactivate, never a hard delete.
 */
@Service
public class CostCenterService {

    private final CostCenterRepository repository;
    private final AuditLogService auditLogService;

    public CostCenterService(CostCenterRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<CostCenterDto> listAll() {
        return repository.findAllByOrderByCodeAsc().stream().map(CostCenterDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CostCenterDto> listActive() {
        return repository.findByActiveTrueOrderByCodeAsc().stream().map(CostCenterDto::from).toList();
    }

    @Transactional(readOnly = true)
    public CostCenterDto getById(Long id) {
        return CostCenterDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public CostCenterDto create(CostCenterRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw ApiException.badRequest("Code is required");
        }
        if (repository.existsByCodeIgnoreCase(request.getCode())) {
            throw ApiException.conflict("A cost center with code '" + request.getCode() + "' already exists");
        }
        CostCenter costCenter = new CostCenter();
        costCenter.setCode(request.getCode().trim());
        applyRequest(costCenter, request);
        CostCenter saved = repository.save(costCenter);
        auditLogService.record("COST_CENTER_CREATED", "Created cost center " + saved.getCode() + " (" + saved.getName() + ")", "CostCenter", saved.getId());
        return CostCenterDto.from(saved);
    }

    @Transactional
    public CostCenterDto update(Long id, CostCenterRequest request) {
        CostCenter costCenter = getEntityOrThrow(id);
        applyRequest(costCenter, request);
        CostCenter saved = repository.save(costCenter);
        auditLogService.record("COST_CENTER_UPDATED", "Updated cost center " + saved.getCode() + " (" + saved.getName() + ")", "CostCenter", saved.getId());
        return CostCenterDto.from(saved);
    }

    /** Package-visible — resolves a cost center by id for callers (ExpenseService/IncomeService/JournalEntryService) that accept an optional costCenterId, same convention as AccountService.getByWellKnownKeyOrThrow. */
    CostCenter getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Cost center not found: " + id));
    }

    private void applyRequest(CostCenter costCenter, CostCenterRequest request) {
        costCenter.setName(request.getName());
        costCenter.setActive(request.isActive());
    }
}
