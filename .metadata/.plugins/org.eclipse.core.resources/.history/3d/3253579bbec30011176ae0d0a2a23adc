package com.posapp.inventory;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.inventory.dto.StockAdjustmentDto;
import com.posapp.inventory.dto.StockAdjustmentRequest;
import com.posapp.settings.StoreSettingsService;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Task: "Inventory depth ... stock adjustments/write-offs with a reason code." Every correction
 * to a product's stock that isn't a sale, return, or purchase-order receipt goes through here
 * instead of the admin editing Product.stockQuantity directly — that's exactly the gap the task
 * called out ("today stock is a single quantity field you edit directly"): now every such change
 * carries a reason code, an optional note, and (via {@link AuditLogService}) who made it and when.
 *
 * <p>Fourier: "Build a full permissions system" — {@link #create} used to apply the change
 * immediately; it now only logs the claim as {@link StockAdjustmentStatus#PENDING}, mirroring
 * {@code com.posapp.restaurant.RestaurantWastageService}. {@link #confirm} (ADMIN or
 * PERM_APPROVE_STOCK_ADJUSTMENT, enforced in {@code StockAdjustmentController}) is what actually
 * applies the stock change and posts to Accounting, and {@link #reject} discards a PENDING entry
 * with no side effects.
 */
@Service
public class StockAdjustmentService {

    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final StoreSettingsService storeSettingsService;
    private final AccountingService accountingService;
    /** Multi-warehouse phase 2 — resolves/validates an adjustment's optional location and reads its per-location quantity. */
    private final WarehouseService warehouseService;
    /** Staff limited to certain branches can only confirm/reject adjustments at their branches' warehouses. */
    private final com.posapp.branch.BranchAccessService branchAccessService;
    /** Batch/expiry and serial numbers added or removed by an adjustment. */
    private final com.posapp.tracking.TrackingService trackingService;

    public StockAdjustmentService(StockAdjustmentRepository stockAdjustmentRepository,
                                   CatalogService catalogService,
                                   AuditLogService auditLogService,
                                   StoreSettingsService storeSettingsService,
                                   AccountingService accountingService,
                                   WarehouseService warehouseService,
                                   com.posapp.branch.BranchAccessService branchAccessService,
                                   com.posapp.tracking.TrackingService trackingService) {
        this.trackingService = trackingService;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
        this.storeSettingsService = storeSettingsService;
        this.accountingService = accountingService;
        this.warehouseService = warehouseService;
        this.branchAccessService = branchAccessService;
    }

    @Transactional(readOnly = true)
    public List<StockAdjustmentDto> listAll() {
        return stockAdjustmentRepository.findAllByOrderByCreatedAtDesc().stream().map(StockAdjustmentDto::from).toList();
    }

    /**
     * Just logs the claim — {@link StockAdjustmentStatus#PENDING}, no real stock/Accounting
     * impact yet. {@code stockBefore}/{@code stockAfter} recorded here are a *preview* (current
     * stock, and what it would become if applied right now) purely so the list screen can show
     * something meaningful before confirmation — {@link #confirm} recomputes both for real against
     * whatever the stock actually is at that later moment.
     */
    @Transactional
    public StockAdjustmentDto create(StockAdjustmentRequest request) {
        if (request.getQuantityDelta() == 0) {
            throw ApiException.badRequest("Adjustment quantity cannot be zero");
        }
        StockAdjustmentReason reason = parseReason(request.getReason());
        Product product = catalogService.getProductOrThrow(request.getProductId());

        // Multi-warehouse phase 2: an optional location; null keeps the legacy product-level figure.
        WarehouseLocation location = null;
        if (request.getLocationId() != null) {
            location = warehouseService.getLocationOrThrow(request.getLocationId());
            requireUsable(location);
        }

        int previewBefore = location != null ? warehouseService.getStockQuantity(product, location) : product.getStockQuantity();
        int previewAfter = previewBefore + request.getQuantityDelta();
        // Task: "Inventory Setting (Example: allow negative stock)" — same preview-time check
        // confirm() repeats for real; this is just early feedback so a doomed request isn't even
        // logged as PENDING.
        if (previewAfter < 0 && !storeSettingsService.getSettings().allowNegativeStock()) {
            throw ApiException.badRequest("Cannot adjust '" + product.getName() + "'" + (location != null ? " at " + describe(location) : "")
                    + " by " + request.getQuantityDelta()
                    + " — current stock is " + previewBefore + ", which would go negative."
                    + " Enable \"Allow negative stock\" in Inventory settings to allow this.");
        }

        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setProduct(product);
        adjustment.setProductName(product.getName());
        adjustment.setQuantityDelta(request.getQuantityDelta());
        if (location != null) {
            adjustment.setLocation(location);
            adjustment.setLocationName(location.getName());
            adjustment.setWarehouseName(location.getWarehouse() != null ? location.getWarehouse().getName() : null);
        }
        adjustment.setStockBefore(previewBefore);
        adjustment.setStockAfter(previewAfter);
        adjustment.setReason(reason);
        adjustment.setNote(request.getNote());
        adjustment.setStatus(StockAdjustmentStatus.PENDING);
        captureTracking(adjustment, product, request);

        StockAdjustment saved = stockAdjustmentRepository.save(adjustment);

        auditLogService.record("STOCK_ADJUSTMENT_LOGGED",
                "Logged a " + (request.getQuantityDelta() > 0 ? "+" : "") + request.getQuantityDelta() + " adjustment to "
                        + product.getName() + (location != null ? " at " + describe(location) : "") + " (" + reason + ") — awaiting approval"
                        + (request.getNote() != null && !request.getNote().isBlank() ? ": " + request.getNote() : ""),
                "Product", product.getId());

        return StockAdjustmentDto.from(saved);
    }

    /**
     * Fourier: "when creating product if the stock quantity is set then it should impact
     * inventory of cause [sic] displayed into Stock Movement History" — {@code
     * CatalogController#createProduct}'s own entry point, called right after a brand-new product
     * is saved with a non-zero starting {@code stockQuantity}. Deliberately not the same path as
     * {@link #create}/{@link #confirm} above: there's no PENDING claim to preview or later
     * re-check against "current stock" (the product didn't exist a moment ago — its stock was
     * exactly 0 right up until {@code CatalogService.createProduct} just saved it as {@code
     * quantity}), and no approval step makes sense for a number the admin just typed into the same
     * form that created the product, so this is recorded already {@link
     * StockAdjustmentStatus#CONFIRMED}. Skipped entirely for a non-tracked product (see {@code
     * Product.trackStock}'s javadoc — its {@code stockQuantity} isn't meaningfully tracked at all)
     * or when the starting quantity is zero (the overwhelmingly common case — most products still
     * start empty and get their first real stock from a Purchase Receipt instead, which already
     * has its own Stock Movement History row).
     *
     * <p>Deliberately posts nothing to Accounting, unlike {@link #confirm}'s call to {@code
     * AccountingService#postStockAdjustment} — opening stock has no purchase/capital-injection
     * entry to balance against yet, so posting one here would just invent one; whatever real cost
     * basis this product's stock has is already captured in its own {@code costPrice}, used
     * normally the moment any of this stock is actually sold.
     */
    @Transactional
    public void recordInitialStock(Long productId, int quantity) {
        if (quantity == 0) {
            return;
        }
        Product product = catalogService.getProductOrThrow(productId);
        if (!product.isTrackStock()) {
            return;
        }

        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setProduct(product);
        adjustment.setProductName(product.getName());
        adjustment.setQuantityDelta(quantity);
        adjustment.setStockBefore(0);
        adjustment.setStockAfter(quantity);
        adjustment.setReason(StockAdjustmentReason.INITIAL_STOCK);
        adjustment.setNote("Initial stock recorded at product creation");
        adjustment.setStatus(StockAdjustmentStatus.CONFIRMED);
        adjustment.setConfirmedAt(Instant.now());

        stockAdjustmentRepository.save(adjustment);

        auditLogService.record("STOCK_ADJUSTMENT_INITIAL_STOCK",
                "Recorded initial stock of " + quantity + " for " + product.getName() + " at creation",
                "Product", product.getId());
    }

    /**
     * ADMIN or PERM_APPROVE_STOCK_ADJUSTMENT (enforced in {@code StockAdjustmentController}) —
     * what {@link #create} used to do inline moves here: re-reads the product's *current* stock
     * (it may have moved since this was logged, e.g. from a sale or another adjustment) and
     * applies this adjustment's {@code quantityDelta} against that real figure, then posts the
     * change to Accounting.
     */
    @Transactional
    public StockAdjustmentDto confirm(Long id) {
        StockAdjustment adjustment = getOrThrow(id);
        branchAccessService.assertCanAccess(warehouseService.branchIdOf(adjustment.getLocation() != null ? adjustment.getLocation().getWarehouse() : null));
        if (adjustment.getStatus() != StockAdjustmentStatus.PENDING) {
            throw ApiException.badRequest("This adjustment is already " + adjustment.getStatus());
        }
        Product product = adjustment.getProduct();
        if (product == null) {
            throw ApiException.badRequest("Cannot confirm this adjustment — its product no longer exists");
        }

        // Multi-warehouse phase 2: a located adjustment re-reads *that location's* current
        // quantity and checks negative stock against it; an un-located (legacy) one keeps using
        // the product-level figure exactly as before.
        WarehouseLocation location = adjustment.getLocation();
        if (location != null) {
            requireUsable(location);
        }
        int stockBefore = location != null ? warehouseService.getStockQuantity(product, location) : product.getStockQuantity();
        int stockAfter = stockBefore + adjustment.getQuantityDelta();
        if (stockAfter < 0 && !storeSettingsService.getSettings().allowNegativeStock()) {
            throw ApiException.badRequest("Cannot confirm this adjustment — current stock of '" + product.getName() + "'"
                    + (location != null ? " at " + describe(location) : "") + " is "
                    + stockBefore + ", and applying " + (adjustment.getQuantityDelta() > 0 ? "+" : "") + adjustment.getQuantityDelta()
                    + " would go negative. Enable \"Allow negative stock\" in Inventory settings to allow this.");
        }

        applyWithTracking(adjustment, product, location);
        adjustment.setStockBefore(stockBefore);
        adjustment.setStockAfter(stockAfter);
        adjustment.setStatus(StockAdjustmentStatus.CONFIRMED);
        adjustment.setConfirmedAt(Instant.now());

        StockAdjustment saved = stockAdjustmentRepository.save(adjustment);

        auditLogService.record("STOCK_ADJUSTMENT_CONFIRMED",
                "Confirmed adjustment of " + saved.getProductName() + " by " + (saved.getQuantityDelta() > 0 ? "+" : "") + saved.getQuantityDelta()
                        + " (" + saved.getReason() + ", now " + stockAfter + ") — inventory and accounting updated",
                "Product", product.getId());

        // Fourier: "all...Inventory...module will be integrated when accounting is enabled" — see
        // AccountingService.postStockAdjustment's own javadoc.
        accountingService.postStockAdjustment(saved);

        return StockAdjustmentDto.from(saved);
    }

    /** ADMIN or PERM_APPROVE_STOCK_ADJUSTMENT (enforced in {@code StockAdjustmentController}) — discards a PENDING entry with no stock/Accounting impact, e.g. logged in error. */
    @Transactional
    public StockAdjustmentDto reject(Long id) {
        StockAdjustment adjustment = getOrThrow(id);
        branchAccessService.assertCanAccess(warehouseService.branchIdOf(adjustment.getLocation() != null ? adjustment.getLocation().getWarehouse() : null));
        if (adjustment.getStatus() != StockAdjustmentStatus.PENDING) {
            throw ApiException.badRequest("This adjustment is already " + adjustment.getStatus());
        }
        adjustment.setStatus(StockAdjustmentStatus.REJECTED);
        StockAdjustment saved = stockAdjustmentRepository.save(adjustment);
        auditLogService.record("STOCK_ADJUSTMENT_REJECTED",
                "Rejected the logged adjustment of " + saved.getProductName() + " by " + (saved.getQuantityDelta() > 0 ? "+" : "") + saved.getQuantityDelta(),
                "Product", saved.getProduct() != null ? saved.getProduct().getId() : null);
        return StockAdjustmentDto.from(saved);
    }

    /** Keeps the batch/serial details typed on a tracked product's adjustment, after checking they fit the quantity. */
    private void captureTracking(StockAdjustment adjustment, Product product, StockAdjustmentRequest request) {
        com.posapp.tracking.TrackingMode mode = com.posapp.tracking.TrackingService.modeOf(product);
        int delta = request.getQuantityDelta();
        if (mode == com.posapp.tracking.TrackingMode.BATCH && delta > 0) {
            adjustment.setTrackingBatch(request.getBatchNumber() != null && !request.getBatchNumber().isBlank() ? request.getBatchNumber().trim() : null);
            adjustment.setTrackingExpiry(request.getExpiryDate());
        } else if (mode == com.posapp.tracking.TrackingMode.SERIAL && request.getSerialNumbers() != null && !request.getSerialNumbers().isEmpty()) {
            List<String> serials = trackingService.cleanSerials(product, request.getSerialNumbers());
            if (serials.size() != Math.abs(delta)) {
                throw ApiException.badRequest("'" + product.getName() + "' is serial-numbered: " + Math.abs(delta) + " unit"
                        + (Math.abs(delta) == 1 ? "" : "s") + " need " + Math.abs(delta) + " serial number" + (Math.abs(delta) == 1 ? "" : "s")
                        + " (" + serials.size() + " given)");
            }
            adjustment.setTrackingSerials(String.join("\n", serials));
        }
    }

    /** Applies the stock change, adding the new batch/serials on an increase, or removing the chosen serials on a decrease. */
    private void applyWithTracking(StockAdjustment adjustment, Product product, WarehouseLocation location) {
        int delta = adjustment.getQuantityDelta();
        com.posapp.tracking.TrackingMode mode = com.posapp.tracking.TrackingService.modeOf(product);
        List<String> serials = adjustment.getTrackingSerials() != null && !adjustment.getTrackingSerials().isBlank()
                ? List.of(adjustment.getTrackingSerials().split("\\R")) : List.of();
        if (delta < 0 && mode == com.posapp.tracking.TrackingMode.SERIAL && !serials.isEmpty()) {
            com.posapp.tracking.TrackingContext.begin(null, java.util.Map.of(product.getId(), serials));
            try {
                catalogService.adjustStock(product, delta, location);
            } finally {
                com.posapp.tracking.TrackingContext.clear();
            }
            return;
        }
        catalogService.adjustStock(product, delta, location);
        if (delta > 0 && mode != com.posapp.tracking.TrackingMode.NONE) {
            WarehouseLocation where = location != null ? location
                    : warehouseService.resolveCanonicalSalesLocation(warehouseService.getDefaultWarehouseOrThrow());
            String source = "Stock adjustment #" + adjustment.getId();
            if (mode == com.posapp.tracking.TrackingMode.BATCH) {
                trackingService.receiveBatch(product, where, adjustment.getTrackingBatch(), adjustment.getTrackingExpiry(), delta, product.getCostPrice(), source);
            } else if (!serials.isEmpty()) {
                trackingService.receiveSerials(product, where, serials, product.getCostPrice(), source);
            }
        }
    }

    /** An adjustment can only target an active location in an active warehouse. */
    private void requireUsable(WarehouseLocation location) {
        if (!location.isActive() || (location.getWarehouse() != null && !location.getWarehouse().isActive())) {
            throw ApiException.badRequest("Location " + describe(location) + " is inactive and cannot be adjusted");
        }
    }

    private static String describe(WarehouseLocation location) {
        String warehouse = location.getWarehouse() != null ? location.getWarehouse().getName() : null;
        return (warehouse != null ? warehouse + " / " : "") + location.getName();
    }

    private StockAdjustment getOrThrow(Long id) {
        return stockAdjustmentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Stock adjustment not found: " + id));
    }

    private StockAdjustmentReason parseReason(String raw) {
        try {
            return StockAdjustmentReason.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ApiException.badRequest("Unknown reason: " + raw);
        }
    }
}
