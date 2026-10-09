package com.posapp.restaurant;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.restaurant.dto.DiningTableDto;
import com.posapp.restaurant.dto.TableRequest;
import com.posapp.restaurant.dto.TableStatusRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin > Restaurant > Tables (Fourier: "add Restaurant management"). Same "reads open to any logged-in user, writes admin-only" shape as CatalogController's categories/brands. */
@Service
public class DiningTableService {

    private final DiningTableRepository tableRepository;
    private final RestaurantOrderRepository orderRepository;
    private final AuditLogService auditLogService;

    public DiningTableService(DiningTableRepository tableRepository, RestaurantOrderRepository orderRepository,
                               AuditLogService auditLogService) {
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<DiningTableDto> listAll() {
        return tableRepository.findAllByOrderByNameAsc().stream()
                .map(t -> DiningTableDto.from(t, openOrderIdFor(t.getId())))
                .toList();
    }

    private Long openOrderIdFor(Long tableId) {
        return orderRepository.findByTableIdAndStatus(tableId, RestaurantOrderStatus.OPEN)
                .map(RestaurantOrder::getId).orElse(null);
    }

    @Transactional
    public DiningTableDto create(TableRequest request) {
        DiningTable table = new DiningTable();
        applyRequest(table, request);
        DiningTable saved = tableRepository.save(table);
        auditLogService.record("RESTAURANT_TABLE_CREATED", "Added table " + saved.getName(), "DiningTable", saved.getId());
        return DiningTableDto.from(saved, null);
    }

    @Transactional
    public DiningTableDto update(Long id, TableRequest request) {
        DiningTable table = getOrThrow(id);
        applyRequest(table, request);
        DiningTable saved = tableRepository.save(table);
        auditLogService.record("RESTAURANT_TABLE_UPDATED", "Updated table " + saved.getName(), "DiningTable", saved.getId());
        return DiningTableDto.from(saved, openOrderIdFor(saved.getId()));
    }

    /**
     * Manual status override — the one status transition set by hand rather than derived from an
     * order opening/closing (see DiningTable.status's own javadoc). Refused while a real order is
     * open at this table, since that status is owned by RestaurantOrderService instead.
     */
    @Transactional
    public DiningTableDto setStatus(Long id, TableStatusRequest request) {
        DiningTable table = getOrThrow(id);
        if (orderRepository.findByTableIdAndStatus(id, RestaurantOrderStatus.OPEN).isPresent()) {
            throw ApiException.badRequest("This table has an open order — close or cancel it before changing the table's status directly");
        }
        TableStatus status;
        try {
            status = TableStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown table status: " + request.getStatus());
        }
        table.setStatus(status);
        DiningTable saved = tableRepository.save(table);
        auditLogService.record("RESTAURANT_TABLE_STATUS_CHANGED", "Table " + saved.getName() + " set to " + status, "DiningTable", saved.getId());
        return DiningTableDto.from(saved, null);
    }

    private void applyRequest(DiningTable table, TableRequest request) {
        table.setName(request.getName());
        table.setSection(request.getSection());
        table.setCapacity(request.getCapacity());
        table.setActive(request.isActive());
    }

    private DiningTable getOrThrow(Long id) {
        return tableRepository.findById(id).orElseThrow(() -> ApiException.notFound("Table not found: " + id));
    }
}
