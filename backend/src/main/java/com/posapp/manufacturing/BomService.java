package com.posapp.manufacturing;
import java.math.BigDecimal;

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

    /** Recipes with search (code, name, product, SKU, a component) and an ACTIVE / INACTIVE choice. */
    @Transactional(readOnly = true)
    public List<BomDto> filtered(String q, String status, Long productId) {
        return repository.findAllByOrderByProduct_NameAsc().stream()
                .filter(b -> status == null || status.isBlank() || (status.equalsIgnoreCase("ACTIVE") == b.isActive()))
                .filter(b -> com.posapp.common.ListPaging.sameId(productId, b.getProduct().getId()))
                .filter(b -> com.posapp.common.ListPaging.has(q, b.getCode(), b.getName(), b.getProduct().getName(), b.getProduct().getSku())
                        || (q != null && !q.isBlank() && b.getLines().stream().anyMatch(l -> com.posapp.common.ListPaging.has(q, l.getComponent().getName(), l.getComponent().getSku()))))
                .map(BomDto::from).toList();
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
        bom.setVersion(nextVersion(request.getProductId()));
        applyRequest(bom, request);
        Bom saved = repository.save(bom);
        auditLogService.record("BOM_CREATED", "Created bill of materials " + saved.getCode() + " for " + saved.getProduct().getName(), "Bom", saved.getId());
        return BomDto.from(saved);
    }

    private int nextVersion(Long productId) {
        return repository.findByProductId(productId).stream().mapToInt(Bom::getVersion).max().orElse(0) + 1;
    }

    /** MF-6: a new, inactive version of a recipe — same components, by-products and routing — to change without touching the one in use. */
    @Transactional
    public BomDto copy(Long id) {
        Bom source = getEntityOrThrow(id);
        Bom bom = new Bom();
        bom.setCode("BOM-" + System.currentTimeMillis());
        bom.setProduct(source.getProduct());
        bom.setRouting(source.getRouting());
        bom.setName((source.getName() != null && !source.getName().isBlank() ? source.getName() : "Recipe") + " (copy)");
        bom.setActive(false);
        bom.setVersion(nextVersion(source.getProduct().getId()));
        for (BomLine l : source.getLines()) {
            BomLine line = new BomLine();
            line.setBom(bom);
            line.setComponent(l.getComponent());
            line.setQuantityPerUnit(l.getQuantityPerUnit());
            bom.getLines().add(line);
        }
        for (BomByProduct b : source.getByProducts()) {
            BomByProduct bp = new BomByProduct();
            bp.setBom(bom);
            bp.setProduct(b.getProduct());
            bp.setQuantityPerUnit(b.getQuantityPerUnit());
            bp.setCostSharePercent(b.getCostSharePercent());
            bom.getByProducts().add(bp);
        }
        Bom saved = repository.save(bom);
        auditLogService.record("BOM_COPIED", "Copied bill of materials " + source.getCode() + " to version " + saved.getVersion() + " (" + saved.getCode() + ") for " + saved.getProduct().getName(), "Bom", saved.getId());
        return BomDto.from(saved);
    }

    /** MF-6: make this version the one used for new orders and production plans; the product's other recipes are switched off. Orders already made keep the recipe they were made with. */
    @Transactional
    public BomDto activate(Long id) {
        Bom bom = getEntityOrThrow(id);
        for (Bom other : repository.findByProductId(bom.getProduct().getId())) {
            if (!other.getId().equals(bom.getId()) && other.isActive()) {
                other.setActive(false);
                repository.save(other);
            }
        }
        bom.setActive(true);
        Bom saved = repository.save(bom);
        auditLogService.record("BOM_ACTIVATED", "Made " + saved.getCode() + " (version " + saved.getVersion() + ") the active recipe for " + saved.getProduct().getName(), "Bom", saved.getId());
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
    public Map<Product, BigDecimal> requiredComponentQuantities(Bom bom, BigDecimal quantityToProduce) {
        Map<Product, BigDecimal> result = new LinkedHashMap<>();
        for (BomLine line : bom.getLines()) {
            result.merge(line.getComponent(), line.getQuantityPerUnit().multiply(quantityToProduce), BigDecimal::add);
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

        // MF-4: by-products
        bom.getByProducts().clear();
        java.math.BigDecimal shareTotal = java.math.BigDecimal.ZERO;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        if (request.getByProducts() != null) {
            for (BomRequest.ByProductEntry entry : request.getByProducts()) {
                if (entry.getProductId().equals(product.getId())) {
                    throw ApiException.badRequest("'" + product.getName() + "' cannot be its own by-product");
                }
                if (!seen.add(entry.getProductId())) {
                    throw ApiException.badRequest("The same by-product is listed twice");
                }
                if (lines.stream().anyMatch(l -> l.getComponent().getId().equals(entry.getProductId()))) {
                    throw ApiException.badRequest("A product cannot be both a component and a by-product of the same recipe");
                }
                BomByProduct bp = new BomByProduct();
                bp.setBom(bom);
                bp.setProduct(catalogService.getProductOrThrow(entry.getProductId()));
                bp.setQuantityPerUnit(entry.getQuantityPerUnit());
                bp.setCostSharePercent(entry.getCostSharePercent() != null ? entry.getCostSharePercent() : java.math.BigDecimal.ZERO);
                shareTotal = shareTotal.add(bp.getCostSharePercent());
                bom.getByProducts().add(bp);
            }
        }
        if (shareTotal.compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest("The by-products' cost shares add up to more than 100%");
        }
    }
}
