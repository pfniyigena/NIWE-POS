package com.posapp.warehouse;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.Branch;
import com.posapp.branch.BranchService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.settings.StoreSettingsService;
import com.posapp.warehouse.dto.ProductStockByLocationDto;
import com.posapp.warehouse.dto.WarehouseDto;
import com.posapp.warehouse.dto.WarehouseLocationDto;
import com.posapp.warehouse.dto.WarehouseLocationRequest;
import com.posapp.warehouse.dto.WarehouseRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * See Warehouse's own javadoc for the overall plan. Deliberately depends on nothing from
 * com.posapp.catalog beyond the Product entity itself (no CatalogService, no ProductRepository):
 * CatalogService is the one that will depend on this service (to call
 * {@link #syncDefaultWarehouseStock}), so keeping the dependency one-directional avoids any risk
 * of the circular-bean problem that com.posapp.inventory.StockAdjustmentService/CatalogService hit
 * earlier (see CatalogController's own javadoc for that precedent) — this service never needs to
 * look a product up itself, every method here that touches a Product is handed one by its caller.
 *
 * <p>Fourier: "What is we give location type of RECEIVING,SALES,STORAGE,DAMAGED" — see {@link
 * LocationType}'s own javadoc for the full design (type drives behavior directly; the old {@code
 * WarehouseLocation.isDefault} flag is gone entirely). This class owns every per-warehouse
 * invariant that goes with it: exactly one RECEIVING location, at least one active SALES location,
 * and the one-time migration off the legacy flag (see {@link #migrateLocationTypesIfNeeded}).
 */
@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseLocationRepository warehouseLocationRepository;
    private final ProductStockRepository productStockRepository;
    private final AuditLogService auditLogService;
    /**
     * Fourier: "Add allow multi-warehouse in inventory settings" — gates {@link #create} only
     * (see that method's own javadoc); never {@link #createLocation}. Safe one-directional
     * dependency: {@code StoreSettingsService} itself depends only on {@code
     * StoreSettingsRepository}/{@code AuditLogService}/{@code MailService}, none of which touch
     * this package, so there's no risk of the circular-bean problem noted on this class's own
     * javadoc.
     */
    private final StoreSettingsService storeSettingsService;
    /** Branches sit above warehouses; BranchService depends on neither this class nor StoreSettingsService, so no cycle. */
    private final BranchService branchService;
    /** The store-wide default low-stock threshold ({@code app.pos.low-stock-threshold}) — what a warehouse with no threshold of its own uses. */
    private final int defaultLowStockThreshold;

    @PersistenceContext
    private EntityManager entityManager;

    public WarehouseService(WarehouseRepository warehouseRepository,
                             WarehouseLocationRepository warehouseLocationRepository,
                             ProductStockRepository productStockRepository,
                             AuditLogService auditLogService,
                             StoreSettingsService storeSettingsService,
                             BranchService branchService,
                             @Value("${app.pos.low-stock-threshold:10}") int defaultLowStockThreshold) {
        this.warehouseRepository = warehouseRepository;
        this.warehouseLocationRepository = warehouseLocationRepository;
        this.productStockRepository = productStockRepository;
        this.auditLogService = auditLogService;
        this.storeSettingsService = storeSettingsService;
        this.branchService = branchService;
        this.defaultLowStockThreshold = defaultLowStockThreshold;
    }

    /** A warehouse's own low-stock threshold, or the store-wide default when it has none (or no warehouse is given). */
    public int effectiveLowStockThreshold(Warehouse warehouse) {
        return warehouse != null && warehouse.getLowStockThreshold() != null ? warehouse.getLowStockThreshold() : defaultLowStockThreshold;
    }

    /** The primary warehouse's effective threshold — what the classic Product.stockQuantity-based low-stock checks use. */
    @Transactional(readOnly = true)
    public int primaryLowStockThreshold() {
        return effectiveLowStockThreshold(getDefaultWarehouseOrThrow());
    }

    @Transactional(readOnly = true)
    public List<WarehouseDto> listAll() {
        return warehouseRepository.findAllByOrderByNameAsc().stream().map(WarehouseDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<WarehouseDto> listActive() {
        return warehouseRepository.findByActiveTrueOrderByNameAsc().stream().map(WarehouseDto::from).toList();
    }

    /**
     * Fourier: "Add allow multi-warehouse in inventory settings. By default multi-warehouse is
     * disabled ... Terminal linked to this warehouse(default)" then "if yes ... To make sale there
     * is a need of creating another warehouse" — while {@code StoreSettings.multiWarehouseEnabled}
     * is off (the default), the store is meant to stay on exactly one warehouse, so a second one
     * is refused here. Follow-up clarification: "When multi-warehouse is OFF ... I can still add
     * location on the default warehouse" — this block is deliberately only in {@code create},
     * never in {@link #createLocation}; adding further locations to the existing warehouse is
     * always allowed regardless of this setting. {@code warehouseRepository.count() >= 1} rather
     * than checking for the primary specifically — there's always at least the seeded "Main"
     * warehouse by the time an admin can reach this screen, so "one already exists" and "the
     * primary already exists" are equivalent in practice, but count() reads correctly even in the
     * unlikely case a second, non-primary warehouse already existed before this flag shipped.
     *
     * <p>Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — every warehouse now needs
     * both a RECEIVING and a SALES location from the moment it's created (see {@link
     * LocationType}'s own javadoc on the "exactly one RECEIVING, at least one active SALES"
     * invariants), so this seeds both, replacing the single "Main Stock" default location this
     * method used to seed alone.
     */
    @Transactional
    public WarehouseDto create(WarehouseRequest request) {
        if (!storeSettingsService.getSettingsEntity().isMultiWarehouseEnabled() && warehouseRepository.count() >= 1) {
            throw ApiException.conflict("Multi-warehouse is turned off — enable it in Inventory settings before adding another warehouse.");
        }
        if (warehouseRepository.existsByNameIgnoreCase(request.name())) {
            throw ApiException.conflict("A warehouse named '" + request.name() + "' already exists");
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setName(request.name());
        warehouse.setAddress(request.address());
        warehouse.setLowStockThreshold(request.lowStockThreshold());
        warehouse.setActive(request.active() == null || request.active());
        warehouse.setBranch(request.branchId() != null
                ? branchService.getEntityOrThrow(request.branchId())
                : branchService.getHeadquartersOrThrow());
        // primary is never settable here — see Warehouse.primary's javadoc.
        Warehouse saved = warehouseRepository.save(warehouse);
        auditLogService.record("WAREHOUSE_CREATED", "Created warehouse '" + saved.getName() + "'",
                "Warehouse", saved.getId());
        WarehouseLocation receiving = createLocationInternal(saved, resolveLocationName(saved, "Receiving"), LocationType.RECEIVING);
        WarehouseLocation sales = createLocationInternal(saved, resolveLocationName(saved, "Main Stock"), LocationType.SALES);
        auditLogService.record("WAREHOUSE_LOCATION_CREATED",
                "Seeded Receiving (" + receiving.getName() + ") and Main Stock (" + sales.getName() + ") locations for warehouse '" + saved.getName() + "'",
                "WarehouseLocation", sales.getId());
        return WarehouseDto.from(saved);
    }

    @Transactional
    public WarehouseDto update(Long id, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Warehouse not found: " + id));
        if (!warehouse.getName().equalsIgnoreCase(request.name()) && warehouseRepository.existsByNameIgnoreCase(request.name())) {
            throw ApiException.conflict("A warehouse named '" + request.name() + "' already exists");
        }
        if (warehouse.isPrimary() && request.active() != null && !request.active()) {
            throw ApiException.conflict("Cannot deactivate '" + warehouse.getName() + "' — it is the primary warehouse");
        }
        warehouse.setName(request.name());
        warehouse.setAddress(request.address());
        warehouse.setLowStockThreshold(request.lowStockThreshold());
        if (request.branchId() != null) {
            warehouse.setBranch(branchService.getEntityOrThrow(request.branchId()));
        }
        if (request.active() != null) {
            warehouse.setActive(request.active());
        }
        Warehouse saved = warehouseRepository.save(warehouse);
        auditLogService.record("WAREHOUSE_UPDATED", "Updated warehouse '" + saved.getName() + "'",
                "Warehouse", saved.getId());
        return WarehouseDto.from(saved);
    }

    /** Same "toggle, don't delete" convention as PackagingController's rule toggle. Refuses to deactivate the primary warehouse — see Warehouse.primary's javadoc. */
    @Transactional
    public WarehouseDto setActive(Long id, boolean active) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Warehouse not found: " + id));
        if (warehouse.isPrimary() && !active) {
            throw ApiException.conflict("Cannot deactivate '" + warehouse.getName() + "' — it is the primary warehouse");
        }
        warehouse.setActive(active);
        Warehouse saved = warehouseRepository.save(warehouse);
        auditLogService.record(active ? "WAREHOUSE_ACTIVATED" : "WAREHOUSE_DEACTIVATED",
                (active ? "Activated" : "Deactivated") + " warehouse '" + saved.getName() + "'",
                "Warehouse", saved.getId());
        return WarehouseDto.from(saved);
    }

    /**
     * The one warehouse every pre-existing stock-moving code path implicitly targets until a later
     * phase lets each of them take a warehouse argument. Assumes {@link #seedDefaultWarehouseIfNeeded}
     * has already run at least once (DataSeeder calls it unconditionally on every startup, before
     * anything that could call this), so this never has to create one lazily itself.
     */
    @Transactional(readOnly = true)
    public Warehouse getDefaultWarehouseOrThrow() {
        return warehouseRepository.findByPrimaryTrue()
                .orElseThrow(() -> ApiException.notFound("No primary warehouse is configured"));
    }

    /** Plain lookup by id, same shape as HelpDeskCategoryService.getEntityOrThrow — used by StockTransferService to resolve the two ends of a transfer. */
    @Transactional(readOnly = true)
    public Warehouse getEntityOrThrow(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Warehouse not found: " + id));
    }

    /** The warehouse's one RECEIVING location — see {@link LocationType}'s own javadoc on why there's always exactly one. Used by PurchaseOrderService/PurchaseReturnService to resolve where a receipt/reversal lands. */
    @Transactional(readOnly = true)
    public WarehouseLocation getReceivingLocationOrThrow(Warehouse warehouse) {
        return warehouseLocationRepository.findByWarehouseAndType(warehouse, LocationType.RECEIVING)
                .orElseThrow(() -> ApiException.notFound("No RECEIVING location configured for warehouse '" + warehouse.getName() + "'"));
    }

    /**
     * Fourier: register-no-explicit-location fallback — used by {@code OrderService}/{@code
     * ReturnsService} when a register has no {@code defaultLocation} pinned and the sale/return
     * has to resolve one from the warehouse alone. Deliberately throws a clear, actionable error
     * when a warehouse's SALES locations are ambiguous (zero, or more than one active) rather than
     * guessing — unlike {@link #resolveCanonicalSalesLocation}, which exists for an internal
     * bookkeeping path that can never throw. Exactly one active SALES location is the expected,
     * common case for a warehouse nobody has split further.
     */
    @Transactional(readOnly = true)
    public WarehouseLocation resolveFallbackSalesLocation(Warehouse warehouse) {
        List<WarehouseLocation> active = warehouseLocationRepository.findAllByWarehouseAndTypeAndActiveTrue(warehouse, LocationType.SALES);
        if (active.isEmpty()) {
            throw ApiException.conflict("Warehouse '" + warehouse.getName()
                    + "' has no active SALES location — add one in Admin > Warehouses, or assign this register a specific location in Admin > Registers.");
        }
        if (active.size() > 1) {
            throw ApiException.conflict("Warehouse '" + warehouse.getName()
                    + "' has more than one active SALES location — assign this register a specific location in Admin > Registers.");
        }
        return active.get(0);
    }

    /**
     * Internal, deliberately non-throwing-on-ambiguity resolution of "the" canonical sellable
     * location {@link #syncDefaultWarehouseStock} and {@code CatalogService#adjustStock} treat as
     * *the* primary warehouse's sales location — the one {@code Product.stockQuantity} always
     * mirrors. Unlike {@link #resolveFallbackSalesLocation}, which exists to throw a clear,
     * actionable error for the register-fallback case, this one is called on literally every stock
     * mutation in the app (via {@code CatalogService.adjustStock}), so it must never throw just
     * because an admin later added a second SALES location — it resolves deterministically instead,
     * to the oldest (lowest id) active SALES location, which for every warehouse that has only ever
     * had the one auto-created/migrated SALES location (the common case) is simply that location.
     * Only throws if the warehouse somehow has zero SALES locations at all — a genuinely broken
     * state the "at least one active SALES" invariant is designed to prevent from ever happening.
     */
    @Transactional(readOnly = true)
    public WarehouseLocation resolveCanonicalSalesLocation(Warehouse warehouse) {
        List<WarehouseLocation> active = warehouseLocationRepository.findAllByWarehouseAndTypeAndActiveTrue(warehouse, LocationType.SALES);
        if (!active.isEmpty()) {
            return active.stream().min(Comparator.comparing(WarehouseLocation::getId)).orElseThrow();
        }
        List<WarehouseLocation> any = warehouseLocationRepository.findAllByWarehouseAndType(warehouse, LocationType.SALES);
        if (!any.isEmpty()) {
            return any.stream().min(Comparator.comparing(WarehouseLocation::getId)).orElseThrow();
        }
        throw ApiException.notFound("Warehouse '" + warehouse.getName() + "' has no SALES location configured");
    }

    /**
     * How much of {@code product} this (any, not just the primary) warehouse currently holds that
     * actually counts as available/sellable — the sum across every active location EXCEPT
     * DAMAGED-typed ones (Fourier: "Excluded from sellable stock" — see {@link LocationType}'s own
     * javadoc), 0 if none of them have ever held this product. Used by StockTransferService to
     * preview/validate a transfer's source side before moving anything.
     */
    @Transactional(readOnly = true)
    public int getStockQuantity(Product product, Warehouse warehouse) {
        return productStockRepository.sumSellableQuantityByProductAndWarehouse(product, warehouse);
    }

    /** Sellable stock of a product across every warehouse (active, non-damaged locations). */
    @Transactional(readOnly = true)
    public int totalSellable(Product product) {
        return productStockRepository.sumSellableQuantityByProduct(product);
    }

    /** Everything on hand for a product across every location of every warehouse, DAMAGED and receiving stock included — the base for a weighted-average cost. */
    @Transactional(readOnly = true)
    public int totalOnHand(Product product) {
        int total = 0;
        for (ProductStock row : productStockRepository.findByProduct(product)) {
            total += row.getQuantity();
        }
        return total;
    }

    /** A warehouse's active locations, as entities — the stock count walks these. */
    @Transactional(readOnly = true)
    public List<WarehouseLocation> activeLocations(Warehouse warehouse) {
        return warehouseLocationRepository.findAllByWarehouseOrderByNameAsc(warehouse).stream().filter(WarehouseLocation::isActive).toList();
    }

    /** Every stock row held at one location (including zero rows). */
    @Transactional(readOnly = true)
    public List<ProductStock> stockRowsAt(WarehouseLocation location) {
        return productStockRepository.findByLocation(location);
    }

    /** Location-level equivalent of {@link #getStockQuantity(Product, Warehouse)} — 0 if this specific location has never held the product. Deliberately not DAMAGED-filtered: a specific location's own row always reads its real quantity regardless of type, including a DAMAGED one — only the warehouse-wide sellable sum excludes it. */
    @Transactional(readOnly = true)
    public int getStockQuantity(Product product, WarehouseLocation location) {
        return productStockRepository.findByProductAndLocation(product, location)
                .map(ProductStock::getQuantity)
                .orElse(0);
    }

    /** Location-level stock delta — creates the row (starting from 0) if this location has never held the product before. Callers are responsible for validating the result won't go negative (the store's "allow negative stock" setting) before calling this — it applies the delta unconditionally. */
    @Transactional
    public void adjustLocationStock(Product product, WarehouseLocation location, int delta) {
        ProductStock stock = productStockRepository.findByProductAndLocation(product, location)
                .orElseGet(() -> {
                    ProductStock created = new ProductStock();
                    created.setProduct(product);
                    created.setLocation(location);
                    return created;
                });
        stock.setQuantity(stock.getQuantity() + delta);
        productStockRepository.save(stock);
    }

    /**
     * Idempotent — same "runs every startup, only ever creates what's missing" convention as
     * AccountService.backfillMissingAccounts/DataSeeder.backfillProductTaxRates. Creates exactly
     * one warehouse, named "Main" and flagged {@link Warehouse#primary}, the first time this ever
     * runs against an empty warehouse table; a no-op on every later startup once it exists. Does
     * NOT create its locations itself — {@link #migrateLocationTypesIfNeeded}, called right after
     * this in {@link #backfillProductStock}, covers every warehouse including this one.
     */
    @Transactional
    public void seedDefaultWarehouseIfNeeded() {
        if (warehouseRepository.findByPrimaryTrue().isPresent()) {
            return;
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setName("Main");
        warehouse.setActive(true);
        warehouse.setPrimary(true);
        Warehouse saved = warehouseRepository.save(warehouse);
        auditLogService.record("WAREHOUSE_CREATED", "Seeded default warehouse '" + saved.getName() + "'",
                "Warehouse", saved.getId());
    }

    /**
     * Branch id to snapshot on a document that was fulfilled by / received into this warehouse:
     * the warehouse's branch (a null warehouse means the primary warehouse), or head office when that has none.
     */
    @Transactional(readOnly = true)
    public Long branchIdOf(Warehouse warehouse) {
        Warehouse effective = warehouse != null ? warehouse : warehouseRepository.findByPrimaryTrue().orElse(null);
        return branchService.idOrHeadquarters(effective != null ? effective.getBranch() : null);
    }

    /** Idempotent startup backfill: every warehouse created before branches existed joins head office. */
    @Transactional
    public void assignMissingBranches() {
        Branch headquarters = branchService.getHeadquartersOrThrow();
        for (Warehouse warehouse : warehouseRepository.findAll()) {
            if (warehouse.getBranch() == null) {
                warehouse.setBranch(headquarters);
                warehouseRepository.save(warehouse);
            }
        }
    }

    private WarehouseLocation createLocationInternal(Warehouse warehouse, String name, LocationType type) {
        WarehouseLocation location = new WarehouseLocation();
        location.setWarehouse(warehouse);
        location.setName(name);
        location.setActive(true);
        location.setType(type);
        return warehouseLocationRepository.save(location);
    }

    /** First choice that isn't already taken by another location in this warehouse — e.g. "Receiving", then "Receiving 2", "Receiving 3", ... Avoids colliding with a location an admin may have already named that exact thing before this migration ran. */
    private String resolveLocationName(Warehouse warehouse, String preferred) {
        String candidate = preferred;
        int suffix = 2;
        while (warehouseLocationRepository.existsByWarehouseAndNameIgnoreCase(warehouse, candidate)) {
            candidate = preferred + " " + suffix;
            suffix++;
        }
        return candidate;
    }

    /**
     * One-time migration off the legacy {@code is_default} flag onto {@link LocationType} (see
     * that enum's own javadoc for the full design) — Fourier: "Main Stock becomes SALES; new empty
     * Receiving is added". Three idempotent steps, safe to run on every startup:
     *
     * <ol>
     *   <li>Defensively drops the NOT NULL constraint {@code is_default} still carries at the DB
     *       level from when it was a real, required Java field. {@link WarehouseLocation} no
     *       longer maps that column at all, so every location created from here on never sets it —
     *       without this, the very next location insert on a real Postgres table would fail the
     *       constraint. Loosening a constraint is additive/non-destructive, so this is safe to run
     *       even after it's already been dropped once.</li>
     *   <li>Backfills every not-yet-typed row ({@code location_type IS NULL} — i.e. every row that
     *       existed before this migration) straight from that legacy flag: {@code is_default=true}
     *       becomes {@link LocationType#SALES} (zero disruption to currently-sellable stock —
     *       every warehouse's existing "Main Stock" stays immediately sellable), anything else
     *       untyped becomes {@link LocationType#STORAGE}.</li>
     *   <li>Ensures every warehouse ends up with at least one SALES location (almost always already
     *       true after step 2; only a genuinely pre-location-era warehouse with zero locations at
     *       all would still need one created here) and exactly one RECEIVING location (never true
     *       yet for any pre-existing warehouse — this is the "new empty Receiving is added" half of
     *       Fourier's decision).</li>
     * </ol>
     *
     * Must run after {@link #seedDefaultWarehouseIfNeeded} and before {@link
     * #migrateProductStockToLocations} (which still reads the legacy {@code is_default} column
     * directly via native SQL for its own, unrelated migration — see that method's own javadoc —
     * so the column has to still be readable, which it always is; only its NOT NULL constraint
     * ever changes here).
     */
    /** True only on a database that still carries the pre-LocationType {@code warehouse_location.is_default} column. */
    private boolean legacyIsDefaultColumnExists() {
        return columnExists("warehouse_location", "is_default");
    }

    private boolean columnExists(String table, String column) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM information_schema.columns " +
                                "WHERE table_schema = current_schema() AND table_name = ?1 AND column_name = ?2")
                .setParameter(1, table)
                .setParameter(2, column)
                .getSingleResult();
        return count != null && count.intValue() > 0;
    }

    @Transactional
    public void migrateLocationTypesIfNeeded() {
        // The legacy flag only exists on databases created before location types; a fresh database never has it.
        if (legacyIsDefaultColumnExists()) {
            entityManager.createNativeQuery("ALTER TABLE warehouse_location ALTER COLUMN is_default DROP NOT NULL").executeUpdate();
            entityManager.createNativeQuery(
                            "UPDATE warehouse_location SET location_type = CASE WHEN is_default = true THEN 'SALES' ELSE 'STORAGE' END " +
                                    "WHERE location_type IS NULL")
                    .executeUpdate();
        }
        for (Warehouse warehouse : warehouseRepository.findAll()) {
            boolean hasAnySalesLocation = !warehouseLocationRepository.findAllByWarehouseAndType(warehouse, LocationType.SALES).isEmpty();
            if (!hasAnySalesLocation) {
                WarehouseLocation sales = createLocationInternal(warehouse, resolveLocationName(warehouse, "Main Stock"), LocationType.SALES);
                auditLogService.record("WAREHOUSE_LOCATION_CREATED",
                        "Seeded Main Stock (SALES) location for warehouse '" + warehouse.getName() + "'",
                        "WarehouseLocation", sales.getId());
            }
            if (warehouseLocationRepository.findByWarehouseAndType(warehouse, LocationType.RECEIVING).isEmpty()) {
                WarehouseLocation receiving = createLocationInternal(warehouse, resolveLocationName(warehouse, "Receiving"), LocationType.RECEIVING);
                auditLogService.record("WAREHOUSE_LOCATION_CREATED",
                        "Seeded Receiving location for warehouse '" + warehouse.getName() + "'",
                        "WarehouseLocation", receiving.getId());
            }
        }
    }

    /**
     * One-time data migration for the move from warehouse-keyed to location-keyed
     * {@link ProductStock} (see that class's own javadoc for the full story): moves any
     * pre-existing row's quantity onto its warehouse's SALES location (the location that carried
     * {@code is_default=true} before {@link #migrateLocationTypesIfNeeded} retyped it — same
     * location, just read by its still-physically-present legacy flag rather than its id, since
     * this method predates that migration and there's no need to change how it finds its target),
     * so stock recorded before this change — including anything a confirmed Stock Transfer already
     * moved into a non-primary warehouse — isn't silently orphaned when the entity stops mapping
     * the old {@code warehouse_id} column. Idempotent: the {@code WHERE location_id IS NULL} guard
     * means this only ever touches rows the new code hasn't already written to, so it's a no-op on
     * every startup after the first one following the upgrade. Must run after {@link
     * #migrateLocationTypesIfNeeded} so every warehouse's SALES location already exists to migrate
     * onto. The old {@code warehouse_id} column itself is left in place afterward (Hibernate's
     * {@code ddl-auto=update} never drops columns) — harmless once this has run; it can be dropped
     * by hand later if desired, but nothing in the app reads it anymore.
     */
    @Transactional
    public void migrateProductStockToLocations() {
        if (!legacyIsDefaultColumnExists() || !columnExists("product_stock", "warehouse_id")) {
            return;
        }
        entityManager.createNativeQuery(
                "UPDATE product_stock ps SET location_id = wl.id " +
                        "FROM warehouse_location wl " +
                        "WHERE ps.location_id IS NULL AND ps.warehouse_id IS NOT NULL " +
                        "AND wl.warehouse_id = ps.warehouse_id AND wl.is_default = true")
                .executeUpdate();
    }

    /**
     * Mirrors every given product's current {@code stockQuantity} into a {@link ProductStock} row
     * against the primary warehouse's canonical SALES location — idempotent top-up, same convention
     * as DataSeeder.backfillProductTaxRates: only ever creates the rows that don't exist yet or
     * corrects ones that drifted, never rewrites a row that's already right. Ensures the default
     * warehouse exists and every warehouse's locations are migrated/typed first (see {@link
     * #migrateLocationTypesIfNeeded} and {@link #migrateProductStockToLocations}), so this is also
     * safe to call on a completely empty database, and safe to call again after upgrading from
     * before this feature shipped.
     */
    @Transactional
    public void backfillProductStock(List<Product> products) {
        seedDefaultWarehouseIfNeeded();
        migrateLocationTypesIfNeeded();
        migrateProductStockToLocations();
        Warehouse defaultWarehouse = getDefaultWarehouseOrThrow();
        WarehouseLocation canonicalSalesLocation = resolveCanonicalSalesLocation(defaultWarehouse);
        for (Product product : products) {
            syncDefaultWarehouseStock(product, canonicalSalesLocation, product.getStockQuantity());
        }
    }

    /**
     * Upserts the primary warehouse's canonical-SALES-location {@link ProductStock} row for this
     * product to exactly {@code quantity} — called by CatalogService right after every write to
     * {@code Product.stockQuantity} (create, update, adjustStock), so the row is always a faithful
     * mirror of it. {@code Product.stockQuantity} is therefore, as of the RECEIVING/SALES split,
     * specifically "sellable stock at the primary warehouse's SALES location" — never RECEIVING,
     * STORAGE, or DAMAGED stock, which only ever move through {@link #adjustLocationStock} directly.
     */
    @Transactional
    public void syncDefaultWarehouseStock(Product product, int quantity) {
        Warehouse defaultWarehouse = getDefaultWarehouseOrThrow();
        syncDefaultWarehouseStock(product, resolveCanonicalSalesLocation(defaultWarehouse), quantity);
    }

    private void syncDefaultWarehouseStock(Product product, WarehouseLocation defaultLocation, int quantity) {
        ProductStock stock = productStockRepository.findByProductAndLocation(product, defaultLocation)
                .orElseGet(() -> {
                    ProductStock created = new ProductStock();
                    created.setProduct(product);
                    created.setLocation(defaultLocation);
                    return created;
                });
        if (stock.getId() != null && stock.getQuantity() == quantity) {
            return;
        }
        stock.setQuantity(quantity);
        productStockRepository.save(stock);
    }

    // ---- Read-only stock visibility (multi-warehouse phase 1) ----

    /**
     * Fourier: multi-warehouse phase 1 ("Stock by location") — where one product's stock sits right
     * now, across every warehouse. Lists every active location (even at 0, so an admin sees the
     * empty ones too) plus any inactive location that still holds stock. Takes a product id rather
     * than a Product because this service deliberately never looks products up itself (see the
     * class javadoc); an unknown id simply yields all zeros.
     */
    @Transactional(readOnly = true)
    public ProductStockByLocationDto stockByLocation(Long productId) {
        Map<Long, Integer> quantities = new HashMap<>();
        for (ProductStock row : productStockRepository.findByProductId(productId)) {
            quantities.put(row.getLocation().getId(), row.getQuantity());
        }
        List<ProductStockByLocationDto.Row> rows = new ArrayList<>();
        int sellableTotal = 0;
        int onHandTotal = 0;
        for (Warehouse warehouse : warehouseRepository.findAllByOrderByNameAsc()) {
            for (WarehouseLocation location : warehouseLocationRepository.findAllByWarehouseOrderByNameAsc(warehouse)) {
                int quantity = quantities.getOrDefault(location.getId(), 0);
                if (!location.isActive() && quantity == 0) {
                    continue;
                }
                boolean sellable = location.isActive() && location.getType() != LocationType.DAMAGED;
                if (sellable) {
                    sellableTotal += quantity;
                }
                onHandTotal += quantity;
                rows.add(new ProductStockByLocationDto.Row(
                        warehouse.getId(), warehouse.getName(), warehouse.isPrimary(),
                        location.getId(), location.getName(),
                        location.getType() != null ? location.getType().name() : null,
                        location.isActive(), sellable, quantity));
            }
        }
        return new ProductStockByLocationDto(productId, sellableTotal, onHandTotal, rows);
    }

    /**
     * Sellable quantity per product id — for one warehouse, or across all of them when {@code
     * warehouseId} is null — in a single grouped query. Backs the warehouse filter on the Stock On
     * Hand report. A product with no entry has never held sellable stock there (treat as 0).
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> sellableQuantitiesByProduct(Long warehouseId) {
        List<Object[]> rows = warehouseId != null
                ? productStockRepository.sumSellableQuantityByProductInWarehouse(getEntityOrThrow(warehouseId))
                : productStockRepository.sumSellableQuantityByProductAllWarehouses();
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return result;
    }

    /**
     * Sellable quantity per product summed over every warehouse of one branch (a warehouse with no
     * branch counts as head office's). Backs the branch mode of the Stock On Hand report.
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> sellableQuantitiesByProductForBranch(Long branchId) {
        Long headquartersId = branchService.headquartersId();
        Map<Long, Integer> result = new HashMap<>();
        for (Warehouse warehouse : warehouseRepository.findAll()) {
            Long warehouseBranchId = warehouse.getBranch() != null ? warehouse.getBranch().getId() : headquartersId;
            if (!warehouseBranchId.equals(branchId)) {
                continue;
            }
            for (Object[] row : productStockRepository.sumSellableQuantityByProductInWarehouse(warehouse)) {
                result.merge(((Number) row[0]).longValue(), ((Number) row[1]).intValue(), Integer::sum);
            }
        }
        return result;
    }

    // ---- Locations admin CRUD (Admin > Inventory > Warehouses > a warehouse's Locations) ----

    @Transactional(readOnly = true)
    public List<WarehouseLocationDto> listLocations(Long warehouseId) {
        Warehouse warehouse = getEntityOrThrow(warehouseId);
        return warehouseLocationRepository.findAllByWarehouseOrderByNameAsc(warehouse).stream()
                .map(WarehouseLocationDto::from).toList();
    }

    /** Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — {@code request.type()} is required; enforces "at most one RECEIVING per warehouse" (see {@link LocationType}'s own javadoc). */
    @Transactional
    public WarehouseLocationDto createLocation(Long warehouseId, WarehouseLocationRequest request) {
        Warehouse warehouse = getEntityOrThrow(warehouseId);
        if (warehouseLocationRepository.existsByWarehouseAndNameIgnoreCase(warehouse, request.name())) {
            throw ApiException.conflict("Warehouse '" + warehouse.getName() + "' already has a location named '" + request.name() + "'");
        }
        LocationType type = parseType(request.type());
        if (type == LocationType.RECEIVING && warehouseLocationRepository.findByWarehouseAndType(warehouse, LocationType.RECEIVING).isPresent()) {
            throw ApiException.conflict("Warehouse '" + warehouse.getName() + "' already has a RECEIVING location — every warehouse can only have one.");
        }
        WarehouseLocation saved = createLocationInternal(warehouse, request.name(), type);
        auditLogService.record("WAREHOUSE_LOCATION_CREATED",
                "Added " + type + " location '" + saved.getName() + "' to warehouse '" + warehouse.getName() + "'",
                "WarehouseLocation", saved.getId());
        return WarehouseLocationDto.from(saved);
    }

    /** Same "toggle, don't delete" convention as #setActive. Refuses to deactivate a warehouse's RECEIVING location (always) or its last active SALES location — see {@link LocationType}'s own javadoc on both invariants. */
    @Transactional
    public WarehouseLocationDto setLocationActive(Long locationId, boolean active) {
        WarehouseLocation location = getLocationOrThrow(locationId);
        if (!active) {
            if (location.getType() == LocationType.RECEIVING) {
                throw ApiException.conflict("Cannot deactivate '" + location.getName() + "' — it is the RECEIVING location for warehouse '"
                        + location.getWarehouse().getName() + "'");
            }
            if (location.getType() == LocationType.SALES
                    && warehouseLocationRepository.findAllByWarehouseAndTypeAndActiveTrue(location.getWarehouse(), LocationType.SALES).size() <= 1) {
                throw ApiException.conflict("Cannot deactivate '" + location.getName() + "' — it is the last active SALES location for warehouse '"
                        + location.getWarehouse().getName() + "'");
            }
        }
        location.setActive(active);
        WarehouseLocation saved = warehouseLocationRepository.save(location);
        auditLogService.record(active ? "WAREHOUSE_LOCATION_ACTIVATED" : "WAREHOUSE_LOCATION_DEACTIVATED",
                (active ? "Activated" : "Deactivated") + " location '" + saved.getName() + "'",
                "WarehouseLocation", saved.getId());
        return WarehouseLocationDto.from(saved);
    }

    /**
     * Fourier: "Type drives behavior directly" — replaces the old {@code setDefaultLocation}
     * entirely. Only ever changes {@link WarehouseLocation#type}, never admin-settable through
     * plain create/update (same convention as {@code Warehouse.primary}). Enforces the same
     * per-warehouse invariants {@link LocationType}'s own javadoc documents: a RECEIVING location
     * can never be retyped away from RECEIVING at all (effectively immutable once set); a SALES
     * location can't be retyped away if it's the warehouse's last active SALES location; and
     * nothing can be retyped TO RECEIVING if the warehouse already has a different RECEIVING
     * location (at most one per warehouse).
     */
    @Transactional
    public WarehouseLocationDto setLocationType(Long locationId, String newTypeName) {
        WarehouseLocation location = getLocationOrThrow(locationId);
        LocationType newType = parseType(newTypeName);
        LocationType currentType = location.getType();
        if (currentType == newType) {
            return WarehouseLocationDto.from(location);
        }
        if (currentType == LocationType.RECEIVING) {
            throw ApiException.conflict("'" + location.getName() + "' is the RECEIVING location for warehouse '"
                    + location.getWarehouse().getName() + "' — it can't be retyped.");
        }
        if (currentType == LocationType.SALES
                && location.isActive()
                && warehouseLocationRepository.findAllByWarehouseAndTypeAndActiveTrue(location.getWarehouse(), LocationType.SALES).size() <= 1) {
            throw ApiException.conflict("Cannot retype '" + location.getName() + "' — it is the last active SALES location for warehouse '"
                    + location.getWarehouse().getName() + "'. Every warehouse needs at least one.");
        }
        if (newType == LocationType.RECEIVING && warehouseLocationRepository.findByWarehouseAndType(location.getWarehouse(), LocationType.RECEIVING).isPresent()) {
            throw ApiException.conflict("Warehouse '" + location.getWarehouse().getName() + "' already has a RECEIVING location.");
        }
        location.setType(newType);
        WarehouseLocation saved = warehouseLocationRepository.save(location);
        auditLogService.record("WAREHOUSE_LOCATION_TYPE_CHANGED",
                "Changed '" + saved.getName() + "' to " + newType + " for warehouse '" + saved.getWarehouse().getName() + "'",
                "WarehouseLocation", saved.getId());
        return WarehouseLocationDto.from(saved);
    }

    private LocationType parseType(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Location type is required");
        }
        try {
            return LocationType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown location type '" + name + "' — expected RECEIVING, SALES, STORAGE, or DAMAGED");
        }
    }

    /**
     * Multi-warehouse phase 4 — a location a production/packaging/restaurant operation may draw
     * from or put stock into: it must be active, in an active warehouse, and not a DAMAGED location
     * (damaged stock isn't usable stock). Any other type is accepted — a factory may consume from
     * STORAGE and a hub may work out of RECEIVING.
     */
    @Transactional(readOnly = true)
    public WarehouseLocation getUsableLocationOrThrow(Long id) {
        WarehouseLocation location = getLocationOrThrow(id);
        assertUsable(location);
        return location;
    }

    /** See {@link #getUsableLocationOrThrow}; also re-checked at the moment stock actually moves, since a location can be deactivated after a document was drafted. */
    public void assertUsable(WarehouseLocation location) {
        if (!location.isActive() || location.getWarehouse() == null || !location.getWarehouse().isActive()) {
            throw ApiException.badRequest("Location '" + location.getName() + "' is inactive and cannot be used");
        }
        if (location.getType() == LocationType.DAMAGED) {
            throw ApiException.badRequest("Location '" + location.getName() + "' is a Damaged location and cannot be used for this");
        }
    }

    /**
     * Multi-warehouse phase 4 — the Restaurant "kitchen" location from Inventory settings, or null
     * (the primary warehouse's normal stock) when none is configured. Fails with an actionable
     * message if the configured location has since been deactivated, rather than silently falling
     * back to Main.
     */
    @Transactional(readOnly = true)
    public WarehouseLocation getRestaurantLocationOrNull() {
        Long id = storeSettingsService.getSettingsEntity().getRestaurantLocationId();
        if (id == null) {
            return null;
        }
        WarehouseLocation location = getLocationOrThrow(id);
        try {
            assertUsable(location);
        } catch (ApiException ex) {
            throw ApiException.badRequest("The restaurant kitchen location set in Inventory settings can't be used: " + ex.getMessage());
        }
        return location;
    }

    /** Stock of a product at a location; a null location means the legacy figure, {@code Product.stockQuantity} (the primary warehouse's normal stock). */
    @Transactional(readOnly = true)
    public int stockAt(Product product, WarehouseLocation location) {
        return location == null ? product.getStockQuantity() : getStockQuantity(product, location);
    }

    /** Plain lookup by id, same shape as {@link #getEntityOrThrow} one level up — also used by RegisterService to resolve/validate Register.defaultLocation and StockTransferService to resolve a transfer's two ends. */
    @Transactional(readOnly = true)
    public WarehouseLocation getLocationOrThrow(Long id) {
        return warehouseLocationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Warehouse location not found: " + id));
    }
}
