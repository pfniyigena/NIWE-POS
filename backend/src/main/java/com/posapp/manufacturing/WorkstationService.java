package com.posapp.manufacturing;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.manufacturing.dto.WorkstationDto;
import com.posapp.manufacturing.dto.WorkstationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD for {@link Workstation} — same shape as {@code PaymentMethodService}. */
@Service
public class WorkstationService {

    private final WorkstationRepository repository;
    private final AuditLogService auditLogService;

    public WorkstationService(WorkstationRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<WorkstationDto> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(WorkstationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkstationDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream().map(WorkstationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public WorkstationDto getById(Long id) {
        return WorkstationDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public WorkstationDto create(WorkstationRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw ApiException.badRequest("Code is required");
        }
        String code = request.getCode().trim();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("A workstation with code '" + code + "' already exists");
        }
        Workstation w = new Workstation();
        w.setCode(code);
        applyRequest(w, request);
        Workstation saved = repository.save(w);
        auditLogService.record("WORKSTATION_CREATED", "Created workstation " + saved.getCode() + " (" + saved.getName() + ")", "Workstation", saved.getId());
        return WorkstationDto.from(saved);
    }

    @Transactional
    public WorkstationDto update(Long id, WorkstationRequest request) {
        Workstation w = getEntityOrThrow(id);
        applyRequest(w, request);
        Workstation saved = repository.save(w);
        auditLogService.record("WORKSTATION_UPDATED", "Updated workstation " + saved.getCode() + " (" + saved.getName() + ")", "Workstation", saved.getId());
        return WorkstationDto.from(saved);
    }

    /** Used by RoutingOperation/ManufacturingOrderService to resolve a workstation reference without exposing the repository outside this package. */
    @Transactional(readOnly = true)
    public Workstation getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Workstation not found: " + id));
    }

    private void applyRequest(Workstation w, WorkstationRequest request) {
        w.setName(request.getName());
        w.setDescription(request.getDescription());
        w.setHourlyRate(request.getHourlyRate() != null ? request.getHourlyRate() : java.math.BigDecimal.ZERO);
        w.setActive(request.isActive());
    }
}
