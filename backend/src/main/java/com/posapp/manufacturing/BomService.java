package com.posapp.manufacturing;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.manufacturing.dto.BomDto;
import com.posapp.manufacturing.dto.BomRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRUD for {@link Bom}, plus the one calculation the rest of the Manufacturing module builds on:
 * {@link #requiredComponentQuantities}. Deliberately single-level (see {@code Bom}'s javadoc) — if
 * this ever grows into multi-level sub-assemblies, this is the one method that would need to
 * become recursive (with cycle detection: a Bom can't include, directly or through another Bom, a
 * component that is itself made from the original product).
 */
@Service
public class BomService {

    private final BomRepository repository;
    private final CatalogService catalogService;
    private final RoutingService routingService;
    private final AuditLogService auditLogService;

    public BomService(BomRepository repository, CatalogService catalogService, RoutingService routingService, AuditLogService auditLogService) {
        this.repository = repository;
        this.catalogService = catalogService;
        this.routingService = routingService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<BomDto> listAll() {
        return repository.findAllByOrderByProduct_NameAsc().stream().map(BomDto::from).toList();
    }

    /** Every manufactured product's default recipe — used by ProductionPlanService.generate to know which products can be planned for. */
    @Transactional(readOnly = true)
    public List<Bom> listActiveBoms() {
        return repository.findByActiveTrueOrderByProduct_NameAsc();
    }

    @Transactional(readOnly = true)
    public List<BomDto> listForProduct(Long productId) {
        return repository.findByProductIdOrderByActiveDesc(productId).stream().map(BomDto::from).toList();
    }

    @Transactional(readOnly = true)
    public BomDto getById(Long id) {
        return BomDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public BomDto create(BomRequest request) {
        Bom bom = new Bom();
        bom.setCode("BOM-" + System.currentTimeMillis());
        applyRequest(bom, request);
        Bom saved = repository.save(bom);
        auditLogService.record("BOM_CREATED", "Created bill of materials " + saved.getCode() + " for " + saved.getProduct().getName(), "Bom", saved.getId());
        return BomDto.from(saved);
    }

    @Transactional
    public BomDto update(Long id, BomRequest request) {
        Bom bom = getEntityOrThrow(id);
        // orphanRemoval on Bom.lines means clearing and re-adding replaces the whole line set —
        // a Bom itself has no partial-progress history to preserve (that lives on whichever
        // Manufacturing Orders snapshotted it), same reasoning as RoutingService.update.
        bom.getLines().clear();
        applyRequest(bom, request);
        Bom saved = repository.save(bom);
        auditLogService.record("BOM_UPDATED", "Updated bill of materials " + saved.getCode() + " for " + saved.getProduct().getName(), "Bom", saved.getId());
        return BomDto.from(saved);
    }

    /** Used by ManufacturingOrderService/ProductionPlanService to resolve a Bom reference without exposing the repository outside this package. */
    @Transactional(readOnly = true)
    public Bom getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bill of materials not found: " + id));
    }

    /** The product's default Bom for planning purposes (ProductionPlanService) — the active one, if there is one, else null (nothing to plan against). */
    @Transactional(readOnly = true)
    public Bom findActiveForProduct(Long productId) {
        return repository.findFirstByProductIdAndActiveTrue(productId).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isManufactured(Long productId) {
        return repository.existsByProductId(productId);
    }

    /**
     * Scales every component's {@code quantityPerUnit} by {@code quantityToProduce} — see {@link
     * BomLine}'s javadoc for why this is exact integer multiplication with no rounding. Keyed by
     * {@link Product} (not id) so a caller can go straight to {@code Product.isTrackStock()}/{@code
     * Product.getStockQuantity()} without a second lookup.
     */
    public Map<Product, Integer> requiredComponentQuantities(Bom bom, int quantityToProduce) {
        Map<Product, Integer> result = new LinkedHashMap<>();
        for (BomLine line : bom.getLines()) {
            result.merge(line.getComponent(), line.getQuantityPerUnit() * quantityToProduce, Integer::sum);
        }
        return result;
    }

    private void applyRequest(Bom bom, BomRequest request) {
        Product product = catalogService.getProductOrThrow(request.getProductId());
        bom.setProduct(product);
        bom.setName(request.getName());
        bom.setRouting(request.getRoutingId() != null ? routingService.getEntityOrThrow(request.getRoutingId()) : null);
        bom.setActive(request.isActive());
        List<BomLine> lines = request.getLines().stream().map(entry -> {
            if (entry.getComponentId().equals(product.getId())) {
                throw ApiException.badRequest("'" + product.getName() + "' cannot be a component of its own bill of materials");
            }
            Product component = catalogService.getProductOrThrow(entry.getComponentId());
            BomLine line = new BomLine();
            line.setBom(bom);
            line.setComponent(component);
            line.setQuantityPerUnit(entry.getQuantityPerUnit());
            return line;
        }).collect(Collectors.toList());
        bom.getLines().addAll(lines);
    }
}
