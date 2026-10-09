package com.posapp.inventory;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.common.ApiException;
import com.posapp.inventory.dto.SupplierPriceDto;
import com.posapp.inventory.dto.SupplierPriceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-supplier product prices; see {@link SupplierPrice}. */
@Service
public class SupplierPriceService {

    private final SupplierPriceRepository repository;
    private final SupplierService supplierService;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;

    public SupplierPriceService(SupplierPriceRepository repository,
                                SupplierService supplierService,
                                CatalogService catalogService,
                                AuditLogService auditLogService) {
        this.repository = repository;
        this.supplierService = supplierService;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<SupplierPriceDto> list(Long supplierId) {
        List<SupplierPrice> rows = supplierId != null
                ? repository.findBySupplierIdOrderByProductNameAsc(supplierId)
                : repository.findAllByOrderBySupplierNameAscProductNameAsc();
        return rows.stream().map(SupplierPriceDto::from).toList();
    }

    /** The price in force today for each product this supplier has one for (latest start date wins). Drives the purchase order form's prefill. */
    @Transactional(readOnly = true)
    public List<SupplierPriceDto> currentForSupplier(Long supplierId) {
        LocalDate today = LocalDate.now();
        Map<Long, SupplierPrice> best = new LinkedHashMap<>();
        for (SupplierPrice p : repository.findBySupplierIdOrderByProductNameAsc(supplierId)) {
            if (p.getProduct() == null) continue;
            if (p.getValidFrom() != null && p.getValidFrom().isAfter(today)) continue;
            if (p.getValidTo() != null && p.getValidTo().isBefore(today)) continue;
            SupplierPrice current = best.get(p.getProduct().getId());
            if (current == null || startOf(p).isAfter(startOf(current))) {
                best.put(p.getProduct().getId(), p);
            }
        }
        return best.values().stream().map(SupplierPriceDto::from)
                .sorted(Comparator.comparing(SupplierPriceDto::productName, Comparator.nullsLast(String::compareToIgnoreCase))).toList();
    }

    @Transactional
    public SupplierPriceDto create(SupplierPriceRequest request) {
        SupplierPrice p = new SupplierPrice();
        apply(p, request);
        SupplierPrice saved = repository.save(p);
        auditLogService.record("SUPPLIER_PRICE_CREATED",
                "Set " + saved.getSupplier().getName() + "'s price for " + saved.getProduct().getName() + " to " + saved.getUnitCost(),
                "SupplierPrice", saved.getId());
        return SupplierPriceDto.from(saved);
    }

    @Transactional
    public SupplierPriceDto update(Long id, SupplierPriceRequest request) {
        SupplierPrice p = repository.findById(id).orElseThrow(() -> ApiException.notFound("Supplier price not found: " + id));
        apply(p, request);
        SupplierPrice saved = repository.save(p);
        auditLogService.record("SUPPLIER_PRICE_UPDATED",
                "Updated " + saved.getSupplier().getName() + "'s price for " + saved.getProduct().getName() + " to " + saved.getUnitCost(),
                "SupplierPrice", saved.getId());
        return SupplierPriceDto.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        SupplierPrice p = repository.findById(id).orElseThrow(() -> ApiException.notFound("Supplier price not found: " + id));
        repository.delete(p);
        auditLogService.record("SUPPLIER_PRICE_DELETED",
                "Removed " + p.getSupplier().getName() + "'s price for " + p.getProduct().getName(), "SupplierPrice", id);
    }

    private void apply(SupplierPrice p, SupplierPriceRequest request) {
        if (request.getValidFrom() != null && request.getValidTo() != null && request.getValidTo().isBefore(request.getValidFrom())) {
            throw ApiException.badRequest("'Valid to' cannot be before 'valid from'");
        }
        p.setSupplier(supplierService.getEntityOrThrow(request.getSupplierId()));
        p.setProduct(catalogService.getProductOrThrow(request.getProductId()));
        p.setUnitCost(request.getUnitCost());
        p.setValidFrom(request.getValidFrom());
        p.setValidTo(request.getValidTo());
        p.setNote(request.getNote());
    }

    private static LocalDate startOf(SupplierPrice p) {
        return p.getValidFrom() != null ? p.getValidFrom() : LocalDate.MIN;
    }
}
