package com.posapp.manufacturing;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.manufacturing.dto.OverheadCostDto;
import com.posapp.manufacturing.dto.OverheadCostRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** CRUD for {@link OverheadCost} — same shape as {@code WorkstationService}. */
@Service
public class OverheadCostService {

    private final OverheadCostRepository repository;
    private final AuditLogService auditLogService;

    public OverheadCostService(OverheadCostRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<OverheadCostDto> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(OverheadCostDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OverheadCostDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream().map(OverheadCostDto::from).toList();
    }

    @Transactional(readOnly = true)
    public OverheadCostDto getById(Long id) {
        return OverheadCostDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public OverheadCostDto create(OverheadCostRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw ApiException.badRequest("Code is required");
        }
        String code = request.getCode().trim();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("An overhead cost with code '" + code + "' already exists");
        }
        OverheadCost o = new OverheadCost();
        o.setCode(code);
        applyRequest(o, request);
        OverheadCost saved = repository.save(o);
        auditLogService.record("OVERHEAD_COST_CREATED", "Created overhead cost " + saved.getCode() + " (" + saved.getName() + ")", "OverheadCost", saved.getId());
        return OverheadCostDto.from(saved);
    }

    @Transactional
    public OverheadCostDto update(Long id, OverheadCostRequest request) {
        OverheadCost o = getEntityOrThrow(id);
        applyRequest(o, request);
        OverheadCost saved = repository.save(o);
        auditLogService.record("OVERHEAD_COST_UPDATED", "Updated overhead cost " + saved.getCode() + " (" + saved.getName() + ")", "OverheadCost", saved.getId());
        return OverheadCostDto.from(saved);
    }

    /** Used by ManufacturingOrderService to resolve a selected overhead pool without exposing the repository outside this package. */
    @Transactional(readOnly = true)
    public OverheadCost getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Overhead cost not found: " + id));
    }

    private void applyRequest(OverheadCost o, OverheadCostRequest request) {
        o.setName(request.getName());
        o.setAllocationMethod(request.getAllocationMethod());
        o.setRate(request.getRate() != null ? request.getRate() : BigDecimal.ZERO);
        o.setActive(request.isActive());
    }
}
