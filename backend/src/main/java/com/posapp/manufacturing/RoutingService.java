package com.posapp.manufacturing;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.manufacturing.dto.RoutingDto;
import com.posapp.manufacturing.dto.RoutingRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD for {@link Routing}, in the same "replace the whole line set on every edit" shape {@code
 * PurchaseOrderService.updateDraft} uses for its lines — a routing has no partial-progress history
 * of its own to preserve (progress lives on a {@code ManufacturingOrder}'s own snapshot, taken at
 * confirm time, never on the Routing itself), so a full replace on update is safe and simplest.
 */
@Service
public class RoutingService {

    private final RoutingRepository repository;
    private final WorkstationService workstationService;
    private final AuditLogService auditLogService;

    public RoutingService(RoutingRepository repository, WorkstationService workstationService, AuditLogService auditLogService) {
        this.repository = repository;
        this.workstationService = workstationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<RoutingDto> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(RoutingDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RoutingDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream().map(RoutingDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RoutingDto getById(Long id) {
        return RoutingDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public RoutingDto create(RoutingRequest request) {
        Routing r = new Routing();
        applyRequest(r, request);
        Routing saved = repository.save(r);
        auditLogService.record("ROUTING_CREATED", "Created routing " + saved.getName(), "Routing", saved.getId());
        return RoutingDto.from(saved);
    }

    @Transactional
    public RoutingDto update(Long id, RoutingRequest request) {
        Routing r = getEntityOrThrow(id);
        // orphanRemoval on Routing.operations means clearing and re-adding replaces the whole
        // operation set in one go — see class javadoc for why that's safe here.
        r.getOperations().clear();
        applyRequest(r, request);
        Routing saved = repository.save(r);
        auditLogService.record("ROUTING_UPDATED", "Updated routing " + saved.getName(), "Routing", saved.getId());
        return RoutingDto.from(saved);
    }

    /** Used by BomService/ManufacturingOrderService to resolve a routing reference without exposing the repository outside this package. */
    @Transactional(readOnly = true)
    public Routing getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Routing not found: " + id));
    }

    private void applyRequest(Routing r, RoutingRequest request) {
        r.setName(request.getName());
        r.setDescription(request.getDescription());
        r.setActive(request.isActive());
        List<RoutingOperation> operations = request.getOperations().stream().map(entry -> {
            RoutingOperation op = new RoutingOperation();
            op.setRouting(r);
            op.setSequence(entry.getSequence());
            op.setName(entry.getName());
            op.setWorkstation(entry.getWorkstationId() != null ? workstationService.getEntityOrThrow(entry.getWorkstationId()) : null);
            op.setEstimatedMinutesPerUnit(entry.getEstimatedMinutesPerUnit() != null ? entry.getEstimatedMinutesPerUnit() : java.math.BigDecimal.ZERO);
            return op;
        }).collect(Collectors.toList());
        r.getOperations().addAll(operations);
    }
}
