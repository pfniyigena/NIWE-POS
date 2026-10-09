package com.posapp.restaurant;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.restaurant.dto.RestaurantWastageDto;
import com.posapp.restaurant.dto.RestaurantWastageRequest;
import com.posapp.settings.StoreSettingsService;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Fourier: "Restaurants need a dedicated wastage module." Every recorded loss goes through here
 * instead of the general Inventory > Stock Adjustments screen (see {@link RestaurantWastage}'s own
 * class javadoc for why this is its own module rather than reusing that one) — each entry still
 * carries a reason code, an optional note, and (via {@link AuditLogService}) who recorded it and
 * when, exactly like a Stock Adjustment does.
 *
 * <p>Fourier: "Restaurant wastage should be confirmed by admin once confirmed inventory and
 * account will be impacted" — unlike a Stock Adjustment, {@link #create} here no longer touches
 * stock or Accounting at all; it only logs the claim as {@link RestaurantWastageStatus#PENDING}.
 * {@link #confirm} (ADMIN-only, enforced in {@code RestaurantWastageController}) is what actually
 * decrements stock and posts to Accounting, and {@link #reject} discards a PENDING entry with no
 * side effects — see {@link RestaurantWastage}'s own javadoc for the full lifecycle.
 */
@Service
public class RestaurantWastageService {

    private final RestaurantWastageRepository wastageRepository;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final StoreSettingsService storeSettingsService;
    private final AccountingService accountingService;
    /** Multi-warehouse phase 4 — the configured kitchen location (null = primary warehouse stock). */
    private final WarehouseService warehouseService;
    /** Staff limited to certain branches can only confirm/reject their branches' wastage. */
    private final com.posapp.branch.BranchAccessService branchAccessService;

    public RestaurantWastageService(RestaurantWastageRepository wastageRepository,
                                      CatalogService catalogService,
                                      AuditLogService auditLogService,
                                      StoreSettingsService storeSettingsService,
                                      AccountingService accountingService,
                                      WarehouseService warehouseService,
                                      com.posapp.branch.BranchAccessService branchAccessService) {
        this.wastageRepository = wastageRepository;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
        this.storeSettingsService = storeSettingsService;
        this.accountingService = accountingService;
        this.warehouseService = warehouseService;
        this.branchAccessService = branchAccessService;
    }

    @Transactional(readOnly = true)
    public List<RestaurantWastageDto> listAll() {
        return wastageRepository.findAllByOrderByCreatedAtDesc().stream().map(RestaurantWastageDto::from).toList();
    }

    /** Just logs the claim — {@link RestaurantWastageStatus#PENDING}, no stock/Accounting impact yet. See this class's own javadoc. */
    @Transactional
    public RestaurantWastageDto create(RestaurantWastageRequest request) {
        if (request.getQuantity() <= 0) {
            throw ApiException.badRequest("Quantity wasted must be greater than zero");
        }
        RestaurantWastageReason reason = parseReason(request.getReason());
        Product product = catalogService.getProductOrThrow(request.getProductId());

        RestaurantWastage wastage = new RestaurantWastage();
        wastage.setProduct(product);
        wastage.setProductName(product.getName());
        wastage.setQuantity(request.getQuantity());
        wastage.setReason(reason);
        wastage.setNote(request.getNote());
        wastage.setStatus(RestaurantWastageStatus.PENDING);
        // Branch of the configured kitchen location (the primary warehouse's when none is set).
        WarehouseLocation kitchen = warehouseService.getRestaurantLocationOrNull();
        wastage.setBranchId(warehouseService.branchIdOf(kitchen != null ? kitchen.getWarehouse() : null));

        RestaurantWastage saved = wastageRepository.save(wastage);

        auditLogService.record("RESTAURANT_WASTAGE_RECORDED",
                "Logged " + request.getQuantity() + " " + product.getName() + " wasted (" + reason + ") — awaiting admin confirmation"
                        + (request.getNote() != null && !request.getNote().isBlank() ? ": " + request.getNote() : ""),
                "Product", product.getId());

        return RestaurantWastageDto.from(saved);
    }

    /**
     * Fourier: "Restaurant wastage should be confirmed by admin once confirmed inventory and
     * account will be impacted" — ADMIN-only (enforced in {@code RestaurantWastageController}).
     * Everything {@code create} used to do inline moves here: decrements real stock (respecting
     * {@code StoreSettings.allowNegativeStock}, same guard {@code StockAdjustmentService} and
     * {@code RestaurantOrderService.validateStockAvailability} already apply) and posts the loss
     * to Accounting — using the product's *current* cost price/stock at confirmation time, not
     * whatever they were when the entry was first logged, since that's when the real-world impact
     * actually happens.
     */
    @Transactional
    public RestaurantWastageDto confirm(Long id) {
        RestaurantWastage wastage = getOrThrow(id);
        if (wastage.getStatus() != RestaurantWastageStatus.PENDING) {
            throw ApiException.badRequest("This wastage entry is already " + wastage.getStatus());
        }
        branchAccessService.assertCanAccess(wastage.getBranchId());
        Product product = wastage.getProduct();

        // Same "only tracked products carry a stock snapshot" rule as every other stock-changing
        // event in this app (e.g. RestaurantOrderService.checkout's non-recipe branch) — a
        // service/made-to-order menu item still logs the loss (and its cost), it just has no stock
        // to move.
        if (product != null && product.isTrackStock()) {
            WarehouseLocation kitchenLocation = warehouseService.getRestaurantLocationOrNull();
            int stockBefore = warehouseService.stockAt(product, kitchenLocation);
            int stockAfter = stockBefore - wastage.getQuantity();
            // Task: "Inventory Setting (Example: allow negative stock)" — same guard StockAdjustmentService
            // and RestaurantOrderService.validateStockAvailability already apply.
            if (stockAfter < 0 && !storeSettingsService.getSettings().allowNegativeStock()) {
                throw ApiException.badRequest("Cannot confirm " + wastage.getQuantity() + " wasted for '" + wastage.getProductName()
                        + "' — current stock is " + stockBefore + ", which would go negative."
                        + " Enable \"Allow negative stock\" in Inventory settings to allow this.");
            }
            catalogService.adjustStock(product, -wastage.getQuantity(), kitchenLocation);
            wastage.setStockWarehouseId(kitchenLocation != null ? kitchenLocation.getWarehouse().getId() : null);
            wastage.setStockBefore(stockBefore);
            wastage.setStockAfter(stockAfter);
        }

        BigDecimal costImpact = product != null && product.getCostPrice() != null
                ? product.getCostPrice().multiply(BigDecimal.valueOf(wastage.getQuantity())).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        wastage.setCostImpact(costImpact);
        wastage.setStatus(RestaurantWastageStatus.CONFIRMED);
        wastage.setConfirmedAt(Instant.now());

        RestaurantWastage saved = wastageRepository.save(wastage);

        auditLogService.record("RESTAURANT_WASTAGE_CONFIRMED",
                "Confirmed " + saved.getQuantity() + " " + saved.getProductName() + " wasted (" + saved.getReason()
                        + ") — inventory and accounting updated",
                "Product", product != null ? product.getId() : null);

        // Fourier: "Restaurants need a dedicated wastage module" — see AccountingService
        // .postRestaurantWastage's own javadoc for why this posts the same Inventory Shrinkage
        // Expense account a generic Stock Adjustment write-off does.
        accountingService.postRestaurantWastage(saved);

        return RestaurantWastageDto.from(saved);
    }

    /** ADMIN-only (enforced in {@code RestaurantWastageController}) — discards a PENDING entry with no stock/Accounting impact, e.g. logged in error. */
    @Transactional
    public RestaurantWastageDto reject(Long id) {
        RestaurantWastage wastage = getOrThrow(id);
        if (wastage.getStatus() != RestaurantWastageStatus.PENDING) {
            throw ApiException.badRequest("This wastage entry is already " + wastage.getStatus());
        }
        branchAccessService.assertCanAccess(wastage.getBranchId());
        wastage.setStatus(RestaurantWastageStatus.REJECTED);
        RestaurantWastage saved = wastageRepository.save(wastage);
        auditLogService.record("RESTAURANT_WASTAGE_REJECTED",
                "Rejected the logged wastage of " + saved.getQuantity() + " " + saved.getProductName(),
                "Product", saved.getProduct() != null ? saved.getProduct().getId() : null);
        return RestaurantWastageDto.from(saved);
    }

    private RestaurantWastage getOrThrow(Long id) {
        return wastageRepository.findById(id).orElseThrow(() -> ApiException.notFound("Wastage entry not found: " + id));
    }

    private RestaurantWastageReason parseReason(String raw) {
        try {
            return RestaurantWastageReason.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ApiException.badRequest("Unknown reason: " + raw);
        }
    }
}
