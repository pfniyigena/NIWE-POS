package com.posapp.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.posapp.warehouse.Warehouse;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByBarcodeAndActiveTrue(String barcode);

    boolean existsByCategoryId(Long categoryId);

    /** Blocks deleting a Brand still in use — see CatalogService.deleteBrand, mirrors existsByCategoryId above. */
    boolean existsByBrandId(Long brandId);

    /** Task: "code can be null but unique" — CatalogService checks this itself before saving so a collision comes back as a clean 409. */
    boolean existsByCodeIgnoreCase(String code);

    /** Fourier: "no CSV import for ... opening stock" — BulkImportService's opening-stock importer looks a row's product up by code first, then by SKU (see findBySkuIgnoreCase) if no code is given. */
    Optional<Product> findByCodeIgnoreCase(String code);

    /** See findByCodeIgnoreCase just above. */
    Optional<Product> findBySkuIgnoreCase(String sku);

    /** Excludes non-tracked products — see Product.trackStock's javadoc and CatalogService.listLowStock. */
    List<Product> findByActiveTrueAndTrackStockTrueAndStockQuantityLessThanOrderByStockQuantityAsc(int threshold);

    /**
     * Multi-warehouse phase 1 — tracked active products whose SELLABLE stock inside one warehouse
     * (active, non-DAMAGED locations) is below {@code threshold}; a product that has never held
     * stock there counts as 0 and is included. Product.stockQuantity is deliberately not used: it
     * only ever reflects the primary warehouse's sales location. See CatalogService#listLowStockInWarehouse.
     */
    @Query("select p from Product p where p.active = true and p.trackStock = true " +
           "and coalesce((select sum(ps.quantity) from ProductStock ps where ps.product = p " +
           "and ps.location.warehouse = :warehouse and ps.location.active = true " +
           "and (ps.location.type is null or ps.location.type <> com.posapp.warehouse.LocationType.DAMAGED)), 0) < :threshold " +
           "order by p.name asc")
    List<Product> findLowStockInWarehouse(@Param("warehouse") Warehouse warehouse, @Param("threshold") int threshold);

    /** Products with a reorder rule set — the Reorder report's candidates. */
    List<Product> findByActiveTrueAndTrackStockTrueAndReorderLevelNotNullOrderByNameAsc();

    /** The variants generated from one template product. */
    List<Product> findByVariantParentIdOrderByNameAsc(Long variantParentId);

    long countByActiveTrue();

    /**
     * q is always a non-null string (possibly empty) — see CatalogService.searchProducts.
     * Binding a null String parameter that's only ever used inside concat()/lower() confuses the
     * PostgreSQL JDBC driver's type inference (it defaults the unspecified-type null to bytea,
     * causing "function lower(bytea) does not exist"), so we deliberately avoid ":q is null" and
     * compare against '' instead — that keeps the parameter's JDBC type unambiguously VARCHAR.
     *
     * Takes a Pageable purely to cap the result size (Task: "check the loading of 100000
     * products") — the terminal always passes an unsorted, single-page request (see
     * CatalogService.searchProducts) since the ORDER BY below is already fixed; this is not the
     * admin table's real pager (that's searchAllForAdmin), just a hard ceiling so selecting
     * "All products" against a huge catalog can't hand the terminal tens of thousands of tiles
     * to render.
     */
    @Query("select p from Product p where p.active = true " +
           "and (:categoryId is null or p.category.id = :categoryId) " +
           "and (:q = '' or lower(p.name) like lower(concat('%', :q, '%')) or lower(p.sku) like lower(concat('%', :q, '%')) or lower(p.code) like lower(concat('%', :q, '%'))) " +
           "order by p.name asc")
    List<Product> search(Long categoryId, String q, Pageable pageable);

    /**
     * Admin product list (Task: "check the loading of 100000 products") — real server-side
     * paging, includes inactive products (unlike search() above) so they can be reactivated, and
     * matches on code too since it's now a second identifier admins search by.
     */
    @Query("select p from Product p where " +
           "(:q = '' or lower(p.name) like lower(concat('%', :q, '%')) or lower(p.sku) like lower(concat('%', :q, '%')) " +
           "or lower(p.barcode) like lower(concat('%', :q, '%')) or lower(p.code) like lower(concat('%', :q, '%')))")
    Page<Product> searchAllForAdmin(String q, Pageable pageable);
}
