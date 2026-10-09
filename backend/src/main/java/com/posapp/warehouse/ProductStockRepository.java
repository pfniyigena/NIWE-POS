package com.posapp.warehouse;

import com.posapp.catalog.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProductAndLocation(Product product, WarehouseLocation location);

    List<ProductStock> findByProduct(Product product);

    List<ProductStock> findByLocation(WarehouseLocation location);

    /**
     * Total of {@code product} across every active, sellable location belonging to
     * {@code warehouse} — what "how much does this warehouse hold that it could actually sell"
     * means now that quantity is tracked per-location (see WarehouseLocation's own javadoc) and
     * typed (see LocationType's own javadoc). Fourier: "Excluded from sellable stock" — a
     * DAMAGED-typed location's quantity is deliberately left out of this sum (it's still tracked,
     * just never counted as available), which is also why a location with no type yet (mid-migration,
     * {@code location_type IS NULL}) is treated as sellable here rather than silently dropped — only
     * an explicit DAMAGED type ever excludes a location. Used by
     * WarehouseService#getStockQuantity(Product, Warehouse).
     */
    @Query("SELECT COALESCE(SUM(ps.quantity), 0) FROM ProductStock ps " +
            "WHERE ps.product = :product AND ps.location.warehouse = :warehouse AND ps.location.active = true " +
            "AND (ps.location.type IS NULL OR ps.location.type <> com.posapp.warehouse.LocationType.DAMAGED)")
    int sumSellableQuantityByProductAndWarehouse(@Param("product") Product product, @Param("warehouse") Warehouse warehouse);

    /** Sellable stock of one product across every warehouse (active, non-damaged locations). */
    @Query("SELECT COALESCE(SUM(ps.quantity), 0) FROM ProductStock ps " +
            "WHERE ps.product = :product AND ps.location.active = true " +
            "AND (ps.location.type IS NULL OR ps.location.type <> com.posapp.warehouse.LocationType.DAMAGED)")
    int sumSellableQuantityByProduct(@Param("product") Product product);

    /** Every location row (any warehouse) holding {@code productId}; backs the "Stock by location" panel — see WarehouseService#stockByLocation. */
    List<ProductStock> findByProductId(Long productId);

    /**
     * Sellable quantity per product across ONE warehouse — one grouped query instead of one per
     * product, so a warehouse-filtered Stock On Hand report stays fast on a large catalog. Same
     * "active, not DAMAGED" definition of sellable as {@link #sumSellableQuantityByProductAndWarehouse}.
     * Rows are {@code [productId, quantity]}; a product that has never held stock there has no row.
     */
    @Query("SELECT ps.product.id, SUM(ps.quantity) FROM ProductStock ps " +
            "WHERE ps.location.warehouse = :warehouse AND ps.location.active = true " +
            "AND (ps.location.type IS NULL OR ps.location.type <> com.posapp.warehouse.LocationType.DAMAGED) " +
            "GROUP BY ps.product.id")
    List<Object[]> sumSellableQuantityByProductInWarehouse(@Param("warehouse") Warehouse warehouse);

    /** Same as {@link #sumSellableQuantityByProductInWarehouse} but across every warehouse (no warehouse predicate — a separate query rather than a nullable parameter, to avoid PostgreSQL's null-parameter type-inference pitfall noted on ProductRepository#search). */
    @Query("SELECT ps.product.id, SUM(ps.quantity) FROM ProductStock ps " +
            "WHERE ps.location.active = true " +
            "AND (ps.location.type IS NULL OR ps.location.type <> com.posapp.warehouse.LocationType.DAMAGED) " +
            "GROUP BY ps.product.id")
    List<Object[]> sumSellableQuantityByProductAllWarehouses();
}
