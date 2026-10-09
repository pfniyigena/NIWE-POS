package com.posapp.catalog;

import com.posapp.alerts.AlertMailService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.dto.BrandDto;
import com.posapp.catalog.dto.BrandRequest;
import com.posapp.catalog.dto.CategoryDto;
import com.posapp.catalog.dto.CategoryRequest;
import com.posapp.catalog.dto.ProductDto;
import com.posapp.catalog.dto.ProductRequest;
import com.posapp.catalog.dto.WarehouseLowStockRow;
import com.posapp.common.ApiException;
import com.posapp.common.PageResponse;
import com.posapp.tax.TaxRateService;
import com.posapp.warehouse.Warehouse;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final TaxRateService taxRateService;
    private final AuditLogService auditLogService;
    private final AlertMailService alertMailService;
    /**
     * Fourier: "multi-location/multi-warehouse tracking" foundation phase — mirrors every write to
     * {@code Product.stockQuantity} below into the default warehouse's ProductStock row. See
     * WarehouseService's own javadoc for why the dependency only ever points this direction (never
     * the reverse), and {@link #adjustStock}/{@link #createProduct}/{@link #updateProduct} for the
     * three call sites that use it.
     */
    private final WarehouseService warehouseService;
    /** Batch/expiry/serial bookkeeping that follows stock leaving a location. */
    private final com.posapp.tracking.TrackingService trackingService;
    /** Same property CatalogController/DashboardService each take as a one-off {@code @Value} parameter — see {@link #adjustStock}. */
    private final int lowStockThreshold;

    public CatalogService(ProductRepository productRepository, CategoryRepository categoryRepository,
                           BrandRepository brandRepository, TaxRateService taxRateService,
                           AuditLogService auditLogService, AlertMailService alertMailService,
                           WarehouseService warehouseService,
                           com.posapp.tracking.TrackingService trackingService,
                           @Value("${app.pos.low-stock-threshold:10}") int lowStockThreshold) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.taxRateService = taxRateService;
        this.auditLogService = auditLogService;
        this.alertMailService = alertMailService;
        this.warehouseService = warehouseService;
        this.trackingService = trackingService;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories() {
        return categoryRepository.findAll().stream().map(CategoryDto::from).toList();
    }

    @Transactional
    public CategoryDto createCategory(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw ApiException.conflict("A category named '" + request.getName() + "' already exists");
        }
        Category category = new Category();
        category.setName(request.getName());
        category.setColorHex(request.getColorHex());
        return CategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found: " + id));
        if (!category.getName().equalsIgnoreCase(request.getName()) && categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw ApiException.conflict("A category named '" + request.getName() + "' already exists");
        }
        category.setName(request.getName());
        category.setColorHex(request.getColorHex());
        return CategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found: " + id));
        if (productRepository.existsByCategoryId(id)) {
            throw ApiException.conflict("Cannot delete '" + category.getName() + "' — products still use this category");
        }
        categoryRepository.delete(category);
    }

    // ---- brands: same simple CRUD shape as categories above, minus the color (Fourier: "Add product brands") ----

    @Transactional(readOnly = true)
    public List<BrandDto> listBrands() {
        return brandRepository.findAll().stream().map(BrandDto::from).toList();
    }

    @Transactional
    public BrandDto createBrand(BrandRequest request) {
        if (brandRepository.existsByNameIgnoreCase(request.getName())) {
            throw ApiException.conflict("A brand named '" + request.getName() + "' already exists");
        }
        Brand brand = new Brand();
        brand.setName(request.getName());
        return BrandDto.from(brandRepository.save(brand));
    }

    @Transactional
    public BrandDto updateBrand(Long id, BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Brand not found: " + id));
        if (!brand.getName().equalsIgnoreCase(request.getName()) && brandRepository.existsByNameIgnoreCase(request.getName())) {
            throw ApiException.conflict("A brand named '" + request.getName() + "' already exists");
        }
        brand.setName(request.getName());
        return BrandDto.from(brandRepository.save(brand));
    }

    @Transactional
    public void deleteBrand(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Brand not found: " + id));
        if (productRepository.existsByBrandId(id)) {
            throw ApiException.conflict("Cannot delete '" + brand.getName() + "' — products still use this brand");
        }
        brandRepository.delete(brand);
    }

    /**
     * readOnly=true keeps the Hibernate session open for the whole method, including the
     * ProductDto::from mapping below — Product.category is a LAZY @ManyToOne, and without an
     * open session at mapping time this throws "could not initialize proxy ... - no Session"
     * the moment a product with a category is loaded (SimpleJpaRepository's own transaction on
     * productRepository.search(...) closes as soon as that call returns, before the stream/map
     * that follows runs). Every method below that maps a Product/Category to a DTO needs the
     * same treatment.
     */
    /** Hard ceiling on terminal search results — see ProductRepository.search's javadoc. */
    private static final int TERMINAL_SEARCH_CAP = 200;

    @Transactional(readOnly = true)
    public List<ProductDto> searchProducts(Long categoryId, String q) {
        // Always pass a non-null string to the repository — see the comment on
        // ProductRepository.search for why binding a null String parameter there breaks on
        // PostgreSQL ("function lower(bytea) does not exist").
        String normalizedQ = (q == null || q.isBlank()) ? "" : q.trim();
        Pageable cap = PageRequest.of(0, TERMINAL_SEARCH_CAP);
        return productRepository.search(categoryId, normalizedQ, cap).stream().map(ProductDto::from).toList();
    }

    /**
     * Admin product list (Task: "check the loading of 100000 products") — real server-side
     * paging via PageResponse, unlike searchProducts (terminal use) also includes deactivated
     * products so they can be reactivated. size is clamped to keep a page request from asking
     * for the entire catalog at once regardless of what the frontend sends.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductDto> listAllForAdmin(String q, int page, int size) {
        String normalizedQ = (q == null || q.isBlank()) ? "" : q.trim();
        int clampedSize = Math.max(1, Math.min(size, 200));
        int clampedPage = Math.max(0, page);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by("name").ascending());
        return PageResponse.from(productRepository.searchAllForAdmin(normalizedQ, pageable).map(ProductDto::from));
    }

    /** Single-product fetch for the admin edit form — replaces the old "load the whole list, find by id" approach, which stopped working once that list was paged. */
    @Transactional(readOnly = true)
    public ProductDto getByIdForAdmin(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
        return ProductDto.from(product);
    }

    /**
     * Task: "Inventory depth ... purchase orders and supplier records ... stock
     * adjustments/write-offs." A plain, non-active-filtered lookup by id — unlike {@link
     * #getActiveProductOrThrow} (which a sale/cart uses, since you can't sell a deactivated
     * product), the {@code com.posapp.inventory} package (purchase order lines, stock adjustments)
     * needs to be able to restock or correct the count of a product regardless of its current
     * active flag — e.g. receiving a PO placed before a product was deactivated, or correcting a
     * stale count on one before reactivating it.
     */
    @Transactional(readOnly = true)
    public Product getProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
    }

    /** Task: "Implement an audit log of who did what and when" — product/price edits are a curated action, see AuditLogService's javadoc. */
    @Transactional
    public ProductDto createProduct(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        Product saved = productRepository.save(product);
        warehouseService.syncDefaultWarehouseStock(saved, saved.getStockQuantity());
        auditLogService.record("PRODUCT_CREATED", "Created product " + saved.getName() + " (price " + saved.getUnitPrice() + ")",
                "Product", saved.getId());
        return ProductDto.from(saved);
    }

    /** Task: "Implement an audit log of who did what and when" — calls out a price change specifically, since that's the field most worth a trail on its own. */
    @Transactional
    public ProductDto updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
        BigDecimal oldPrice = product.getUnitPrice();
        applyRequest(product, request);
        Product saved = productRepository.save(product);
        warehouseService.syncDefaultWarehouseStock(saved, saved.getStockQuantity());
        String description = "Updated product " + saved.getName();
        if (oldPrice == null || oldPrice.compareTo(saved.getUnitPrice()) != 0) {
            description += ": price " + oldPrice + " -> " + saved.getUnitPrice();
        }
        auditLogService.record("PRODUCT_UPDATED", description, "Product", saved.getId());
        return ProductDto.from(saved);
    }

    /** Soft-delete: products may be referenced by historical order lines, so we deactivate rather than remove the row. */
    @Transactional
    public void deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
        product.setActive(false);
        productRepository.save(product);
        auditLogService.record("PRODUCT_DEACTIVATED", "Deactivated product " + product.getName(), "Product", product.getId());
    }

    /**
     * Fourier: "barcode/label generation for products that don't already carry a manufacturer
     * barcode" — assigns this product a brand-new, internally-generated barcode. Only acts when
     * the product doesn't already have one: this is meant for a product that arrived with no
     * manufacturer barcode at all, not a way to overwrite one that's already set (an admin who
     * wants to replace an existing value clears the field first, same as any other edit).
     * <p>
     * The generated value is a real, valid EAN-13 — not a made-up string — using the {@code 20}
     * prefix GS1 reserves worldwide for a company's own internal/in-store use (never allocated to
     * a real manufacturer's product), so it can never collide with an actual scanned-in barcode.
     * The next ten digits are this product's own id, zero-padded, which makes the whole scheme a
     * deterministic one-to-one mapping from product id to barcode — i.e. already guaranteed unique
     * across products without a separate existence check — and the final digit is the standard
     * GS1/EAN check digit (see {@link #eanCheckDigit}), so the result scans cleanly on any barcode
     * reader, including the same scanner this app's own checkout barcode lookup already uses.
     */
    @Transactional
    public ProductDto generateBarcode(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
        if (product.getBarcode() != null && !product.getBarcode().isBlank()) {
            throw ApiException.conflict("This product already has a barcode — clear it first if you want to generate a new one");
        }
        String payload = "20" + String.format("%010d", product.getId());
        String barcode = payload + eanCheckDigit(payload);
        product.setBarcode(barcode);
        Product saved = productRepository.save(product);
        auditLogService.record("PRODUCT_BARCODE_GENERATED", "Generated barcode " + barcode + " for product " + saved.getName(),
                "Product", saved.getId());
        return ProductDto.from(saved);
    }

    /** The standard GS1/EAN check digit for a 12-digit payload: digits at odd positions (1st, 3rd, ...) weighted ×1, even positions (2nd, 4th, ...) weighted ×3, counting from the left. */
    private static char eanCheckDigit(String twelveDigits) {
        int sum = 0;
        for (int i = 0; i < twelveDigits.length(); i++) {
            int digit = twelveDigits.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int check = (10 - (sum % 10)) % 10;
        return (char) ('0' + check);
    }

    /**
     * Fourier: "Add track stock attribute on Product" — a non-tracked product can always be sold
     * regardless of its stockQuantity (see Product.trackStock's javadoc), so it never belongs on a
     * low-stock alert either; excluded at the query level rather than filtered after, same as the
     * existing activeTrue filter.
     */
    /** Low stock for the primary warehouse's classic figure, using the primary warehouse's own threshold (store-wide default when it has none). */
    @Transactional(readOnly = true)
    public List<ProductDto> listLowStockPrimary() {
        return listLowStock(warehouseService.primaryLowStockThreshold());
    }

    @Transactional(readOnly = true)
    public List<ProductDto> listLowStock(int threshold) {
        return productRepository.findByActiveTrueAndTrackStockTrueAndStockQuantityLessThanOrderByStockQuantityAsc(threshold)
                .stream().map(ProductDto::from).toList();
    }

    /**
     * Multi-warehouse phase 1: the low-stock list for ONE warehouse, using that warehouse's own
     * sellable quantity (see ProductRepository#findLowStockInWarehouse) instead of the primary
     * warehouse's {@code Product.stockQuantity}. Lowest quantity first, like {@link #listLowStock}.
     */
    @Transactional(readOnly = true)
    public List<WarehouseLowStockRow> listLowStockInWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseService.getEntityOrThrow(warehouseId);
        // The warehouse's own threshold, or the store-wide default when it has none.
        return productRepository.findLowStockInWarehouse(warehouse, warehouseService.effectiveLowStockThreshold(warehouse)).stream()
                .map(p -> new WarehouseLowStockRow(p.getId(), p.getSku(), p.getName(), warehouseService.getStockQuantity(p, warehouse)))
                .sorted(java.util.Comparator.comparingInt(WarehouseLowStockRow::quantity))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countActiveProducts() {
        return productRepository.countByActiveTrue();
    }

    /**
     * `product` already carries its current (pre-update) code here — a create passes a fresh
     * `new Product()` whose code is null, an update passes the entity as loaded from the
     * repository — so comparing the incoming code against product.getCode() before overwriting
     * it tells us whether the code actually changed, the same "only re-check if it actually
     * changed" shape as updateCategory's name check above. That means an update that leaves an
     * already-unique code untouched never trips over its own existing row.
     */
    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setBarcode(request.getBarcode());
        String normalizedCode = (request.getCode() == null || request.getCode().isBlank()) ? null : request.getCode().trim();
        boolean codeChanged = !java.util.Objects.equals(normalizedCode, product.getCode());
        if (normalizedCode != null && codeChanged && productRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw ApiException.conflict("A product with code '" + normalizedCode + "' already exists");
        }
        product.setCode(normalizedCode);
        product.setIconDataUrl(request.getIconDataUrl());
        product.setDescription(request.getDescription());
        product.setUnitPrice(request.getUnitPrice());
        product.setCostPrice(request.getCostPrice() != null ? request.getCostPrice() : java.math.BigDecimal.ZERO);
        product.setStockQuantity(request.getStockQuantity());
        product.setTrackStock(request.isTrackStock());
        product.setActive(request.isActive());
        product.setUnspCode(request.getUnspCode());
        product.setService(request.isService());
        product.setReorderLevel(request.getReorderLevel());
        if (request.getTrackingMode() != null) {
            product.setTrackingMode(com.posapp.tracking.TrackingMode.of(request.getTrackingMode()));
        }
        product.setMaxStockLevel(request.getMaxStockLevel());
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> ApiException.notFound("Category not found: " + request.getCategoryId()));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }
        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> ApiException.notFound("Brand not found: " + request.getBrandId()));
            product.setBrand(brand);
        } else {
            product.setBrand(null);
        }
        if (request.getTaxRateId() != null) {
            product.setTaxRate(taxRateService.getEntityOrThrow(request.getTaxRateId()));
        } else {
            product.setTaxRate(null);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductDto> listVariants(Long templateId) {
        return productRepository.findByVariantParentIdOrderByNameAsc(templateId).stream().map(ProductDto::from).toList();
    }

    /**
     * Generates one sellable product per combination of the given options (Size x Colour ...) from a template
     * product, copying its price, cost, category, brand, tax and reorder rules. Each variant gets its own SKU
     * (template SKU plus the option values), barcode, price and stock. The first time, the template must hold no
     * stock and is then deactivated so only its variants are sold; running it again later only adds missing
     * combinations. At most three options and 200 combinations per run.
     */
    @Transactional
    public com.posapp.catalog.dto.VariantResultDto generateVariants(Long templateId, com.posapp.catalog.dto.VariantRequest request) {
        Product template = productRepository.findById(templateId)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + templateId));
        if (template.getVariantParentId() != null) {
            throw ApiException.badRequest("'" + template.getName() + "' is already a variant — generate variants from its template instead");
        }
        List<com.posapp.catalog.dto.VariantRequest.Attribute> attributes = request.getAttributes();
        if (attributes.size() > 3) {
            throw ApiException.badRequest("Use at most three options (e.g. Size, Colour, Material)");
        }
        List<String> names = new java.util.ArrayList<>();
        List<List<String>> valueLists = new java.util.ArrayList<>();
        long combinations = 1;
        for (com.posapp.catalog.dto.VariantRequest.Attribute a : attributes) {
            String name = a.getName().trim();
            if (names.stream().anyMatch(n -> n.equalsIgnoreCase(name))) {
                throw ApiException.badRequest("Option '" + name + "' is listed twice");
            }
            List<String> values = new java.util.ArrayList<>();
            for (String raw : a.getValues()) {
                String v = raw == null ? "" : raw.trim();
                if (!v.isEmpty() && values.stream().noneMatch(x -> x.equalsIgnoreCase(v))) values.add(v);
            }
            if (values.isEmpty()) {
                throw ApiException.badRequest("Option '" + name + "' needs at least one value");
            }
            names.add(name);
            valueLists.add(values);
            combinations *= values.size();
        }
        if (combinations > 200) {
            throw ApiException.badRequest("That would create " + combinations + " variants — generate at most 200 at a time");
        }
        boolean firstRun = template.getVariantAttributes() == null;
        if (firstRun && (template.getStockQuantity() != 0 || warehouseService.totalOnHand(template) != 0)) {
            throw ApiException.badRequest("'" + template.getName() + "' holds stock. Move that stock to its variants (adjust it to zero here, then add stock to each variant) before generating variants.");
        }

        java.util.Set<String> existingLabels = new java.util.HashSet<>();
        for (Product v : productRepository.findByVariantParentIdOrderByNameAsc(templateId)) {
            if (v.getVariantLabel() != null) existingLabels.add(v.getVariantLabel().toLowerCase());
        }
        String baseSku = template.getSku() != null && !template.getSku().isBlank() ? template.getSku().trim() : "P" + template.getId();

        // Cartesian product of the option values, in the order given.
        List<List<String>> combos = new java.util.ArrayList<>();
        combos.add(new java.util.ArrayList<>());
        for (List<String> values : valueLists) {
            List<List<String>> next = new java.util.ArrayList<>();
            for (List<String> prefix : combos) {
                for (String v : values) {
                    List<String> c = new java.util.ArrayList<>(prefix);
                    c.add(v);
                    next.add(c);
                }
            }
            combos = next;
        }

        int created = 0;
        int skipped = 0;
        for (List<String> combo : combos) {
            String label = String.join(" / ", combo);
            if (!existingLabels.add(label.toLowerCase())) {
                skipped++;
                continue;
            }
            Product v = new Product();
            v.setName(template.getName() + " - " + label);
            v.setSku(baseSku + "-" + combo.stream()
                    .map(s -> s.replaceAll("[^A-Za-z0-9]", "").toUpperCase())
                    .map(s -> s.length() > 4 ? s.substring(0, 4) : s)
                    .collect(java.util.stream.Collectors.joining("-")));
            v.setDescription(template.getDescription());
            v.setIconDataUrl(template.getIconDataUrl());
            v.setUnitPrice(template.getUnitPrice());
            v.setCostPrice(template.getCostPrice() != null ? template.getCostPrice() : BigDecimal.ZERO);
            v.setStockQuantity(0);
            v.setTrackStock(template.isTrackStock());
            v.setActive(true);
            v.setCategory(template.getCategory());
            v.setBrand(template.getBrand());
            v.setTaxRate(template.getTaxRate());
            v.setUnspCode(template.getUnspCode());
            v.setService(template.isService());
            v.setReorderLevel(template.getReorderLevel());
            v.setTrackingMode(template.getTrackingMode());
            v.setMaxStockLevel(template.getMaxStockLevel());
            v.setVariantParentId(template.getId());
            v.setVariantLabel(label);
            Product saved = productRepository.save(v);
            warehouseService.syncDefaultWarehouseStock(saved, 0);
            created++;
        }

        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) summary.append(" | ");
            summary.append(names.get(i)).append(": ").append(String.join(", ", valueLists.get(i)));
        }
        template.setVariantAttributes(summary.toString());
        if (firstRun) {
            template.setActive(false);
        }
        productRepository.save(template);
        auditLogService.record("PRODUCT_VARIANTS_GENERATED",
                "Generated " + created + " variant" + (created == 1 ? "" : "s") + " of " + template.getName() + " (" + summary + ")"
                        + (skipped > 0 ? ", " + skipped + " already existed" : ""),
                "Product", template.getId());
        return new com.posapp.catalog.dto.VariantResultDto(created, skipped, listVariants(templateId));
    }

    /** Used by OrderService when adding a line/checking out, and by the returns flow when restocking. */
    @Transactional(readOnly = true)
    public Product getActiveProductOrThrow(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + productId));
        if (!product.isActive()) {
            throw ApiException.badRequest("Product is no longer available: " + product.getName());
        }
        return product;
    }

    @Transactional(readOnly = true)
    public Product getByBarcodeOrThrow(String barcode) {
        return productRepository.findByBarcodeAndActiveTrue(barcode)
                .orElseThrow(() -> ApiException.notFound("No product matches barcode: " + barcode));
    }

    /**
     * Used only by OrderService.syncOfflineSale — deliberately *not* active-only, unlike
     * {@link #getActiveProductOrThrow}. A sale rung offline was built against whatever the
     * terminal's local catalog cache held at the time; if that product has since been deactivated
     * (never hard-deleted — see Product.active), the sale still genuinely happened and still needs
     * to replay, so this looks the product up by id regardless of its current active flag.
     */
    @Transactional(readOnly = true)
    public Product getProductForOfflineSync(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + productId));
    }

    /**
     * Primes the terminal's offline product cache (Task: "local queuing of sales that sync once
     * connectivity returns") — every active product, capped well above the terminal's own live
     * search cap (see TERMINAL_SEARCH_CAP) since this is meant to cover the *whole* sellable
     * catalog for offline lookup, not one search's worth of results. Open to any logged-in user
     * (not admin-only), same tier as searchProducts — the terminal, not just Admin, calls this.
     * Still a fixed ceiling, not unbounded: a catalog larger than this (see the seed script used
     * for the 100,000-product stress test) can't be fully cached for offline use in this pass —
     * documented as a known limitation, same shape as every other fixed cap in this codebase.
     */
    private static final int OFFLINE_CACHE_CAP = 5000;

    @Transactional(readOnly = true)
    public List<ProductDto> listForOfflineCache() {
        Pageable cap = PageRequest.of(0, OFFLINE_CACHE_CAP);
        return productRepository.search(null, "", cap).stream().map(ProductDto::from).toList();
    }

    /**
     * Used by OrderService/ReturnsService (and every other stock-mutating module — see this
     * method's own call sites) to adjust stock without exposing the repository outside this
     * package. The single universal chokepoint for every stock mutation in the app, which makes
     * it the one place to detect a tracked product's stock crossing below
     * {@link #lowStockThreshold} (Fourier: "Wiring a handful of business events to that same
     * mailer") — fired only on the actual crossing (before &gt;= threshold, after &lt; threshold),
     * never on every sale of an already-low product, so this doesn't re-alert on each subsequent
     * unit sold once it's already below the line.
     */
    @Transactional
    public void adjustStock(Product product, int delta) {
        trackOutgoing(product, delta, null);
        applyPlainStock(product, delta);
    }

    /**
     * Batch/serial products: when stock leaves a location the matching batches (earliest expiry
     * first) or serial numbers leave with it. A null location means the primary warehouse's sales location,
     * which is what the plain overload mutates.
     */
    private void trackOutgoing(Product product, int delta, WarehouseLocation location) {
        if (delta >= 0 || com.posapp.tracking.TrackingService.modeOf(product) == com.posapp.tracking.TrackingMode.NONE) {
            return;
        }
        WarehouseLocation where = location;
        if (where == null) {
            Warehouse primary = warehouseService.getDefaultWarehouseOrThrow();
            where = warehouseService.resolveCanonicalSalesLocation(primary);
        }
        trackingService.consume(product, where, -delta);
    }

    private void applyPlainStock(Product product, int delta) {
        int before = product.getStockQuantity();
        int after = before + delta;
        product.setStockQuantity(after);
        productRepository.save(product);
        warehouseService.syncDefaultWarehouseStock(product, after);
        if (product.isTrackStock() && after < before) {
            // The primary warehouse may carry its own threshold; falls back to the store-wide one.
            int threshold = warehouseService.primaryLowStockThreshold();
            if (before >= threshold && after < threshold) {
                alertMailService.alertLowStock(product);
            }
        }
    }

    /**
     * Location-granular variant of {@link #adjustStock(Product, int)} — Fourier: "if multi-location
     * then sale start from location", later refined by Fourier: "location type of
     * RECEIVING,SALES,STORAGE,DAMAGED". {@code location} null, or the primary warehouse's own
     * canonical SALES location (see {@code WarehouseService#resolveCanonicalSalesLocation}),
     * behaves identically to the plain {@link #adjustStock(Product, int)} overload (updates {@code
     * Product.stockQuantity}, fires the low-stock alert) — any other location instead adjusts only
     * that location's own stock via {@code WarehouseService#adjustLocationStock}, deliberately
     * leaving {@code Product.stockQuantity}/the alert untouched. This is also what keeps a Purchase
     * Order/Return's receipt onto a RECEIVING location (see {@code PurchaseOrderService#receiveLines}
     * / {@code PurchaseReturnService#processReturn}) from ever touching {@code
     * Product.stockQuantity} — RECEIVING is never the canonical SALES location, so it always falls
     * through to the location-only path, which is exactly the point: received stock isn't sellable
     * until an explicit Stock Transfer moves it onto a SALES location. Used by OrderService/
     * ReturnsService (a sale/return against whichever location a register resolves to — see
     * Register.defaultLocation's javadoc), PurchaseOrderService/PurchaseReturnService (receipt/
     * reversal onto RECEIVING), and StockTransferService#moveStock (both ends of a transfer); every
     * other stock-moving module (Stock Adjustments, Manufacturing, Packaging, Restaurant) keeps
     * calling the plain {@link #adjustStock(Product, int)} overload instead — Fourier's own
     * "Purchase routing" decision scoped the RECEIVING/SALES split to Purchase Order/Return only.
     */
    @Transactional
    public void adjustStock(Product product, int delta, WarehouseLocation location) {
        trackOutgoing(product, delta, location);
        applyLocationStock(product, delta, location);
    }

    /**
     * Same as {@link #adjustStock(Product, int, WarehouseLocation)} but leaves batches and serial numbers alone —
     * for callers that move them on their own (stock transfers).
     */
    @Transactional
    public void adjustStockUntracked(Product product, int delta, WarehouseLocation location) {
        applyLocationStock(product, delta, location);
    }

    private void applyLocationStock(Product product, int delta, WarehouseLocation location) {
        if (location == null) {
            applyPlainStock(product, delta);
            return;
        }
        Warehouse warehouse = location.getWarehouse();
        if (warehouse != null && warehouse.isPrimary()
                && location.getId().equals(warehouseService.resolveCanonicalSalesLocation(warehouse).getId())) {
            applyPlainStock(product, delta);
            return;
        }
        // Per-warehouse low-stock alert: the primary warehouse already alerts through the plain
        // overload above (Product.stockQuantity). For any other warehouse, compare that warehouse's
        // sellable total (same definition the dashboard's "Low stock by warehouse" uses) before and
        // after, and alert only on the actual crossing below the threshold.
        boolean watchWarehouse = product.isTrackStock() && warehouse != null && !warehouse.isPrimary();
        int warehouseBefore = watchWarehouse ? warehouseService.getStockQuantity(product, warehouse) : 0;
        warehouseService.adjustLocationStock(product, location, delta);
        if (watchWarehouse) {
            int warehouseAfter = warehouseService.getStockQuantity(product, warehouse);
            int warehouseThreshold = warehouseService.effectiveLowStockThreshold(warehouse);
            if (warehouseBefore >= warehouseThreshold && warehouseAfter < warehouseThreshold) {
                alertMailService.alertLowStock(product, warehouse.getName(), warehouseAfter);
            }
        }
    }

    /**
     * Fourier: "When Breakdown product the cost of the child should be equal to the cost of the
     * parent divided by unit" — {@code com.posapp.packaging.PackagingService#breakdown}'s own
     * entry point for recomputing a child product's cost every time a breakdown actually runs.
     * Deliberately its own minimal method rather than routing through {@link #updateProduct}/
     * {@link ProductRequest} (which would also revalidate and overwrite every other field on the
     * product from a full edit form) — this only ever touches {@code costPrice}.
     */
    /**
     * Weighted-average costing on purchase receipt. {@code quantityOnHandBefore} is everything the
     * product held (all warehouses and locations) just before this delivery was added;
     * {@code receivedNetValue} is the delivered slice's value net of tax and discount. A product with no
     * stock, or with no cost set yet, simply takes the delivery's unit cost. Returns the new cost, or
     * null when nothing changed.
     */
    @Transactional
    public BigDecimal applyWeightedAverageCost(Product product, int quantityOnHandBefore, int quantityReceived, BigDecimal receivedNetValue) {
        if (quantityReceived <= 0 || receivedNetValue == null) return null;
        BigDecimal oldCost = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
        int onHand = Math.max(0, quantityOnHandBefore);
        BigDecimal newCost;
        if (onHand == 0 || oldCost.signum() <= 0) {
            newCost = receivedNetValue.divide(BigDecimal.valueOf(quantityReceived), 2, java.math.RoundingMode.HALF_UP);
        } else {
            newCost = oldCost.multiply(BigDecimal.valueOf(onHand)).add(receivedNetValue)
                    .divide(BigDecimal.valueOf((long) onHand + quantityReceived), 2, java.math.RoundingMode.HALF_UP);
        }
        if (newCost.compareTo(oldCost) == 0) return null;
        product.setCostPrice(newCost);
        productRepository.save(product);
        return newCost;
    }

    /** Last-purchase costing: the delivery's own unit cost (including any landed cost share) replaces the product cost. Returns the new cost, or null when unchanged. */
    @Transactional
    public BigDecimal applyLastPurchaseCost(Product product, int quantityReceived, BigDecimal receivedNetValue) {
        if (quantityReceived <= 0 || receivedNetValue == null) return null;
        BigDecimal oldCost = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
        BigDecimal newCost = receivedNetValue.divide(BigDecimal.valueOf(quantityReceived), 2, java.math.RoundingMode.HALF_UP);
        if (newCost.compareTo(oldCost) == 0) return null;
        product.setCostPrice(newCost);
        productRepository.save(product);
        return newCost;
    }

    @Transactional
    public void updateCostPrice(Product product, BigDecimal costPrice) {
        product.setCostPrice(costPrice);
        productRepository.save(product);
    }
}
