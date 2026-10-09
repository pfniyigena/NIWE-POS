package com.posapp.inventory;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.inventory.dto.SupplierDto;
import com.posapp.inventory.dto.SupplierRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Task: "Inventory depth ... purchase orders and supplier records." Suppliers are managed
 * independently of any one purchase order — an admin sets them up once (Admin > Inventory >
 * Suppliers) and then picks from the list when drafting a PO.
 */
@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AuditLogService auditLogService;
    private final SupplierPriceRepository supplierPriceRepository;

    public SupplierService(SupplierRepository supplierRepository,
                            PurchaseOrderRepository purchaseOrderRepository,
                            AuditLogService auditLogService,
                            SupplierPriceRepository supplierPriceRepository) {
        this.supplierPriceRepository = supplierPriceRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<SupplierDto> listAll() {
        return supplierRepository.findAllByOrderByNameAsc().stream().map(SupplierDto::from).toList();
    }

    /** Powers the "supplier" picker on the new-purchase-order form — retired suppliers stay out of new POs but keep their name on old ones. */
    @Transactional(readOnly = true)
    public List<SupplierDto> listActive() {
        return supplierRepository.findByActiveTrueOrderByNameAsc().stream().map(SupplierDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Supplier getEntityOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Supplier not found: " + id));
    }

    @Transactional
    public SupplierDto create(SupplierRequest request) {
        if (supplierRepository.existsByNameIgnoreCase(request.getName())) {
            throw ApiException.conflict("A supplier named '" + request.getName() + "' already exists");
        }
        Supplier supplier = new Supplier();
        applyRequest(supplier, request);
        Supplier saved = supplierRepository.save(supplier);
        auditLogService.record("SUPPLIER_CREATED", "Added supplier " + saved.getName(), "Supplier", saved.getId());
        return SupplierDto.from(saved);
    }

    @Transactional
    public SupplierDto update(Long id, SupplierRequest request) {
        Supplier supplier = getEntityOrThrow(id);
        if (!supplier.getName().equalsIgnoreCase(request.getName()) && supplierRepository.existsByNameIgnoreCase(request.getName())) {
            throw ApiException.conflict("A supplier named '" + request.getName() + "' already exists");
        }
        applyRequest(supplier, request);
        Supplier saved = supplierRepository.save(supplier);
        auditLogService.record("SUPPLIER_UPDATED", "Updated supplier " + saved.getName(), "Supplier", saved.getId());
        return SupplierDto.from(saved);
    }

    /**
     * A supplier already referenced by a purchase order can't be hard-deleted — that PO's own
     * "who was this ordered from" would go dangling — so this only ever deactivates it in that
     * case (same "can't delete, still deactivate" fallback CatalogService.deactivateProduct uses
     * for products already sold). A never-used supplier is removed outright, same as
     * CatalogService.deleteCategory does for an unused category.
     */
    @Transactional
    public void delete(Long id) {
        Supplier supplier = getEntityOrThrow(id);
        if (purchaseOrderRepository.existsBySupplierId(id)) {
            supplier.setActive(false);
            supplierRepository.save(supplier);
            auditLogService.record("SUPPLIER_UPDATED", "Deactivated supplier " + supplier.getName() + " (has purchase order history, so kept rather than deleted)", "Supplier", supplier.getId());
            return;
        }
        supplierPriceRepository.deleteAll(supplierPriceRepository.findBySupplierIdOrderByProductNameAsc(id));
        supplierRepository.delete(supplier);
        auditLogService.record("SUPPLIER_DELETED", "Deleted supplier " + supplier.getName(), "Supplier", id);
    }

    private void applyRequest(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.getName());
        supplier.setContactName(request.getContactName());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setTaxId(request.getTaxId());
        supplier.setAddress(request.getAddress());
        supplier.setNotes(request.getNotes());
        supplier.setActive(request.isActive());
    }
}
