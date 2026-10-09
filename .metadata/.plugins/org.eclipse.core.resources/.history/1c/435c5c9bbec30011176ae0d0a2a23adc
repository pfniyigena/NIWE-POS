package com.posapp.packaging;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.packaging.dto.PackagingBreakdownDto;
import com.posapp.packaging.dto.PackagingBreakdownRequest;
import com.posapp.packaging.dto.PackagingRuleDto;
import com.posapp.packaging.dto.PackagingRuleRequest;
import com.posapp.settings.StoreSettingsService;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Fourier: "I want to add in the POS: Product Packaging &amp; Breakdown / Unit Conversion ...
 * this will be configured as we did for chart of accounts. If not activated ignore packaging."
 *
 * <p>Research note on "configured as we did for chart of accounts": the Chart of Accounts
 * mechanism ({@code StoreSettings.chartCountry}) is actually a mandatory one-time setup wizard
 * with no real on/off switch — the Accounting module itself has no enable/disable switch at all
 * (see {@code AccountService}'s own comment to that effect). A true "if not activated, ignore the
 * whole feature" toggle already exists elsewhere in this app: {@code
 * StoreSettings.allowNegativeStock}/{@code discountMode} — a boolean on the singleton settings
 * row, its own request DTO ({@code InventorySettingsRequest}), edited on its own admin screen
 * (Inventory settings), checked at the point of action. That is the convention mirrored here via
 * {@code StoreSettings.productBreakdownEnabled}, not the chart-of-accounts wizard.
 *
 * <p>Configuring a {@link PackagingRule} (CRUD below) is never gated by the toggle — same
 * "configuration vs. execution" split {@code Bom} has from {@code
 * ManufacturingOrderService#produce} — only {@link #breakdown} is. {@link #breakdown} itself
 * mirrors {@code ManufacturingOrderService#produce}'s trackStock-per-side / allowNegativeStock
 * guard pattern, but deliberately as a single immediate step with no draft/confirm/approval
 * lifecycle (unlike ManufacturingOrder's DRAFT→CONFIRMED→PRODUCED or StockAdjustment's
 * PENDING→CONFIRMED): a bar operator cracking open a bottle happens many times a shift, and an
 * approval step for every one would be pure friction with no real control benefit. It also
 * deliberately posts nothing to accounting — a breakdown is a value-neutral repackaging (the same
 * stock value just now sits under a different product/unit), not a cost or revenue event; profit
 * is still recognized normally, later, whenever a resulting child unit is actually sold through
 * its own existing {@code costPrice}/{@code unitPrice}.
 *
 * <p>Fourier: "When Breakdown product the cost of the child should be equal to the cost of the
 * parent divided by unit" — every {@link #breakdown} run recomputes {@code
 * childProduct.costPrice} as {@code parentProduct.costPrice / unitsPerParent} (2dp, half-up, same
 * rounding every other money figure in this app uses) and writes it via {@code CatalogService
 * #updateCostPrice}, overwriting whatever the child's cost was set to before. This is
 * deliberately recomputed on every run rather than only once — if the parent's own cost changes
 * (a new purchase price, say), the next breakdown re-derives the child's cost from the parent's
 * current figure rather than leaving it stale.
 */
@Service
public class PackagingService {

    private final PackagingRuleRepository ruleRepository;
    private final PackagingBreakdownRepository breakdownRepository;
    private final CatalogService catalogService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    /** Multi-warehouse phase 4 — optional per-breakdown location. */
    private final WarehouseService warehouseService;

    public PackagingService(PackagingRuleRepository ruleRepository,
                             PackagingBreakdownRepository breakdownRepository,
                             CatalogService catalogService,
                             StoreSettingsService storeSettingsService,
                             AuditLogService auditLogService,
                             WarehouseService warehouseService) {
        this.ruleRepository = ruleRepository;
        this.breakdownRepository = breakdownRepository;
        this.catalogService = catalogService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
        this.warehouseService = warehouseService;
    }

    @Transactional(readOnly = true)
    public List<PackagingRuleDto> listRules() {
        return ruleRepository.findAllByOrderByParentProduct_NameAsc().stream().map(PackagingRuleDto::from).toList();
    }

    /** Offered to the breakdown-execution screen's rule picker — a paused rule can't start a new breakdown. */
    @Transactional(readOnly = true)
    public List<PackagingRuleDto> listActiveRules() {
        return ruleRepository.findByActiveTrueOrderByParentProduct_NameAsc().stream().map(PackagingRuleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PackagingRuleDto getRuleById(Long id) {
        return PackagingRuleDto.from(getRuleEntityOrThrow(id));
    }

    @Transactional
    public PackagingRuleDto createRule(PackagingRuleRequest request) {
        PackagingRule rule = new PackagingRule();
        applyRequest(rule, request, null);
        PackagingRule saved = ruleRepository.save(rule);
        auditLogService.record("PACKAGING_RULE_CREATED",
                "Created packaging rule: 1 x " + saved.getParentProduct().getName() + " -> " + saved.getUnitsPerParent() + " x " + saved.getChildProduct().getName(),
                "PackagingRule", saved.getId());
        return PackagingRuleDto.from(saved);
    }

    @Transactional
    public PackagingRuleDto updateRule(Long id, PackagingRuleRequest request) {
        PackagingRule rule = getRuleEntityOrThrow(id);
        applyRequest(rule, request, id);
        PackagingRule saved = ruleRepository.save(rule);
        auditLogService.record("PACKAGING_RULE_UPDATED",
                "Updated packaging rule: 1 x " + saved.getParentProduct().getName() + " -> " + saved.getUnitsPerParent() + " x " + saved.getChildProduct().getName(),
                "PackagingRule", saved.getId());
        return PackagingRuleDto.from(saved);
    }

    /** Toggle, don't delete — see PackagingRule.active's javadoc. */
    @Transactional
    public PackagingRuleDto setRuleActive(Long id, boolean active) {
        PackagingRule rule = getRuleEntityOrThrow(id);
        rule.setActive(active);
        PackagingRule saved = ruleRepository.save(rule);
        auditLogService.record(active ? "PACKAGING_RULE_RESUMED" : "PACKAGING_RULE_PAUSED",
                (active ? "Resumed" : "Paused") + " packaging rule " + saved.getParentProduct().getName() + " -> " + saved.getChildProduct().getName(),
                "PackagingRule", saved.getId());
        return PackagingRuleDto.from(saved);
    }

    /**
     * The one method that actually moves stock — see class javadoc for the toggle/lifecycle/
     * accounting reasoning. Decrements {@code parentQuantity} units of the rule's parent product
     * and increments {@code parentQuantity * unitsPerParent} units of its child product, atomically,
     * recording one {@link PackagingBreakdown} row with before/after stock snapshots on whichever
     * side(s) are actually stock-tracked.
     */
    @Transactional
    public PackagingBreakdownDto breakdown(PackagingBreakdownRequest request) {
        if (!storeSettingsService.getSettings().productBreakdownEnabled()) {
            throw ApiException.badRequest("Product packaging & breakdown is turned off — enable \"Product packaging & breakdown\" in Inventory settings first.");
        }
        PackagingRule rule = getRuleEntityOrThrow(request.getRuleId());
        if (!rule.isActive()) {
            throw ApiException.badRequest("Packaging rule '" + rule.getParentProduct().getName() + " -> " + rule.getChildProduct().getName() + "' is paused — resume it first.");
        }
        int parentQuantity = request.getParentQuantity();
        Product parent = rule.getParentProduct();
        Product child = rule.getChildProduct();

        // Multi-warehouse phase 4: optional location for both sides; null = legacy primary stock.
        WarehouseLocation location = request.getLocationId() != null
                ? warehouseService.getUsableLocationOrThrow(request.getLocationId())
                : null;

        boolean allowNegativeStock = storeSettingsService.getSettings().allowNegativeStock();
        if (!allowNegativeStock && parent.isTrackStock()) {
            int available = warehouseService.stockAt(parent, location);
            if (parentQuantity > available) {
                throw ApiException.badRequest("Not enough stock of '" + parent.getName() + "' to break down — " + available
                        + " available, " + parentQuantity + " requested. Enable \"Allow negative stock\" in Inventory settings to break down anyway.");
            }
        }

        PackagingBreakdown breakdown = new PackagingBreakdown();
        breakdown.setRule(rule);
        breakdown.setParentProduct(parent);
        breakdown.setParentProductName(parent.getName());
        breakdown.setChildProduct(child);
        breakdown.setChildProductName(child.getName());
        breakdown.setUnitsPerParent(rule.getUnitsPerParent());
        breakdown.setParentQuantity(parentQuantity);
        int childQuantityProduced = parentQuantity * rule.getUnitsPerParent();
        breakdown.setChildQuantityProduced(childQuantityProduced);
        breakdown.setNote(request.getNote());
        if (location != null) {
            breakdown.setLocation(location);
            breakdown.setLocationName(location.getName());
            breakdown.setWarehouseName(location.getWarehouse().getName());
        }

        // Fourier: "Add track stock attribute on Product" — a non-tracked product on either side
        // is never checked or adjusted, same treatment ManufacturingOrderService.produce already
        // gives trackStock on both its component and finished-good sides.
        if (parent.isTrackStock()) {
            int stockBefore = warehouseService.stockAt(parent, location);
            catalogService.adjustStock(parent, -parentQuantity, location);
            breakdown.setParentStockBefore(stockBefore);
            breakdown.setParentStockAfter(warehouseService.stockAt(parent, location));
        }
        if (child.isTrackStock()) {
            int stockBefore = warehouseService.stockAt(child, location);
            catalogService.adjustStock(child, childQuantityProduced, location);
            breakdown.setChildStockBefore(stockBefore);
            breakdown.setChildStockAfter(warehouseService.stockAt(child, location));
        }

        // Fourier: "the cost of the child should be equal to the cost of the parent divided by
        // unit" — see class javadoc. Independent of trackStock above: cost matters for margin
        // reporting regardless of whether stock is actually counted for this product.
        BigDecimal parentCost = parent.getCostPrice() != null ? parent.getCostPrice() : BigDecimal.ZERO;
        BigDecimal childUnitCost = parentCost.divide(BigDecimal.valueOf(rule.getUnitsPerParent()), 2, RoundingMode.HALF_UP);
        catalogService.updateCostPrice(child, childUnitCost);
        breakdown.setChildUnitCost(childUnitCost);

        PackagingBreakdown saved = breakdownRepository.save(breakdown);
        auditLogService.record("PACKAGING_BROKEN_DOWN",
                "Broke down " + parentQuantity + " x " + parent.getName() + " into " + childQuantityProduced + " x " + child.getName()
                        + " (child cost set to " + childUnitCost + " per unit)",
                "PackagingBreakdown", saved.getId());
        return PackagingBreakdownDto.from(saved);
    }

    /** Full history, or one rule's own history when ruleId is given — backs the breakdown screen's history table. */
    @Transactional(readOnly = true)
    public List<PackagingBreakdownDto> listBreakdowns(Long ruleId) {
        List<PackagingBreakdown> breakdowns = ruleId != null
                ? breakdownRepository.findByRuleIdOrderByCreatedAtDesc(ruleId)
                : breakdownRepository.findAllByOrderByCreatedAtDesc();
        return breakdowns.stream().map(PackagingBreakdownDto::from).toList();
    }

    private PackagingRule getRuleEntityOrThrow(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Packaging rule not found: " + id));
    }

    private void applyRequest(PackagingRule rule, PackagingRuleRequest request, Long excludeId) {
        if (request.getParentProductId().equals(request.getChildProductId())) {
            throw ApiException.badRequest("A product cannot be broken down into itself");
        }
        Product parent = catalogService.getProductOrThrow(request.getParentProductId());
        Product child = catalogService.getProductOrThrow(request.getChildProductId());
        ruleRepository.findByParentProductIdAndChildProductId(parent.getId(), child.getId())
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("A packaging rule from '" + parent.getName() + "' to '" + child.getName() + "' already exists");
                });
        rule.setParentProduct(parent);
        rule.setChildProduct(child);
        rule.setUnitsPerParent(request.getUnitsPerParent());
        rule.setNotes(request.getNotes());
        rule.setActive(request.isActive());
    }
}
