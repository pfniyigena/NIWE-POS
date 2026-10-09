package com.posapp.catalog;

import com.posapp.catalog.dto.BrandDto;
import com.posapp.catalog.dto.BrandRequest;
import com.posapp.catalog.dto.CategoryDto;
import com.posapp.catalog.dto.CategoryRequest;
import com.posapp.catalog.dto.ProductDto;
import com.posapp.catalog.dto.ProductRequest;
import com.posapp.catalog.dto.WarehouseLowStockRow;
import com.posapp.common.PageResponse;
import com.posapp.inventory.StockAdjustmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;
    /**
     * Fourier: "when creating product if the stock quantity is set then it should impact
     * inventory of cause [sic] displayed into Stock Movement History" — this controller, not
     * CatalogService itself, is what calls StockAdjustmentService#recordInitialStock right after
     * a new product is created: StockAdjustmentService already depends on CatalogService (for
     * its own product lookups), so the reverse dependency would be circular. A controller calling
     * two services in sequence has no such restriction.
     */
    private final StockAdjustmentService stockAdjustmentService;
    private final int lowStockThreshold;

    public CatalogController(CatalogService catalogService,
                              StockAdjustmentService stockAdjustmentService,
                              @Value("${app.pos.low-stock-threshold:10}") int lowStockThreshold) {
        this.catalogService = catalogService;
        this.stockAdjustmentService = stockAdjustmentService;
        this.lowStockThreshold = lowStockThreshold;
    }

    // ---- categories: reads open to any logged-in user (the terminal needs them), writes admin-only ----

    @GetMapping("/categories")
    public List<CategoryDto> listCategories() {
        return catalogService.listCategories();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCategory(@PathVariable Long id) {
        catalogService.deleteCategory(id);
    }

    // ---- brands: same read-open/write-admin shape as categories above (Fourier: "Add product brands") ----

    @GetMapping("/brands")
    public List<BrandDto> listBrands() {
        return catalogService.listBrands();
    }

    @PostMapping("/brands")
    @PreAuthorize("hasRole('ADMIN')")
    public BrandDto createBrand(@Valid @RequestBody BrandRequest request) {
        return catalogService.createBrand(request);
    }

    @PutMapping("/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BrandDto updateBrand(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return catalogService.updateBrand(id, request);
    }

    @DeleteMapping("/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteBrand(@PathVariable Long id) {
        catalogService.deleteBrand(id);
    }

    // ---- products: search (active only) open to any logged-in user, everything else admin-only ----

    @GetMapping("/products")
    public List<ProductDto> searchProducts(@RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) String q) {
        return catalogService.searchProducts(categoryId, q);
    }

    /**
     * Task: "check the loading of 100000 products" — this used to return every product in one
     * unbounded List; now a real page (page/size, plus an optional search q), returned as a
     * PageResponse envelope. size is capped server-side in CatalogService regardless of what's
     * requested here.
     */
    @GetMapping("/products/all")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ProductDto> listAllProducts(@RequestParam(required = false) String q,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return catalogService.listAllForAdmin(q, page, size);
    }

    /** Single-product fetch for the admin edit form — see CatalogService.getByIdForAdmin's javadoc. */
    @GetMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDto getProduct(@PathVariable Long id) {
        return catalogService.getByIdForAdmin(id);
    }

    /**
     * Task: "local queuing of sales that sync once connectivity returns" — the terminal calls this
     * while online (on load, and periodically) to prime/refresh its offline product cache in
     * IndexedDB. See CatalogService.listForOfflineCache's javadoc for the cap.
     */
    @GetMapping("/products/offline-cache")
    public List<ProductDto> listForOfflineCache() {
        return catalogService.listForOfflineCache();
    }

    @GetMapping("/products/low-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProductDto> listLowStock() {
        return catalogService.listLowStockPrimary();
    }

    /** Multi-warehouse phase 1 — low stock inside one warehouse; see CatalogService#listLowStockInWarehouse. */
    @GetMapping("/products/low-stock-by-warehouse")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WarehouseLowStockRow> listLowStockInWarehouse(@RequestParam Long warehouseId) {
        return catalogService.listLowStockInWarehouse(warehouseId);
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDto createProduct(@Valid @RequestBody ProductRequest request) {
        ProductDto dto = catalogService.createProduct(request);
        // Fourier: "when creating product if the stock quantity is set then it should impact
        // inventory of cause [sic] displayed into Stock Movement History" — see
        // StockAdjustmentService#recordInitialStock's own javadoc for why this isn't just folded
        // into CatalogService.createProduct itself.
        stockAdjustmentService.recordInitialStock(dto.id(), dto.stockQuantity());
        return dto;
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDto updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return catalogService.updateProduct(id, request);
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivateProduct(@PathVariable Long id) {
        catalogService.deactivateProduct(id);
    }

    /** Fourier: "barcode/label generation for products that don't already carry a manufacturer barcode" — see CatalogService.generateBarcode's own javadoc. */
    /** The variants generated from a template product. */
    @GetMapping("/products/{id}/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProductDto> listVariants(@PathVariable Long id) {
        return catalogService.listVariants(id);
    }

    /** Generates variants (Size x Colour ...) from a template product — see CatalogService#generateVariants. */
    @PostMapping("/products/{id}/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public com.posapp.catalog.dto.VariantResultDto generateVariants(@PathVariable Long id, @Valid @RequestBody com.posapp.catalog.dto.VariantRequest request) {
        return catalogService.generateVariants(id, request);
    }

    @PostMapping("/products/{id}/generate-barcode")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDto generateBarcode(@PathVariable Long id) {
        return catalogService.generateBarcode(id);
    }
}
