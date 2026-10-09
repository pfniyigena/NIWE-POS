package com.posapp.dataimport;

import com.posapp.catalog.Brand;
import com.posapp.catalog.BrandRepository;
import com.posapp.catalog.Category;
import com.posapp.catalog.CategoryRepository;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.catalog.ProductRepository;
import com.posapp.catalog.dto.ProductRequest;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.dataimport.dto.ImportResultDto;
import com.posapp.dataimport.dto.SkippedRowDto;
import com.posapp.tax.TaxRateRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fourier: "no CSV import for products/customers/opening stock ... 'I need to bulk-load my
 * existing product list' is unanswered today." Three independent importers sharing one CSV
 * reading/row-counting shape; per Fourier's own answer on conflicts, every one of them is
 * partial-success — a row that fails is skipped and reported (see {@link ImportResultDto}), the
 * rest of the file still goes in, nothing is ever all-or-nothing.
 * <p>
 * {@link #importProducts} deliberately reuses {@link CatalogService#createProduct} rather than
 * saving a {@code Product} directly — that's the one place product-create validation and the
 * {@code existsByCodeIgnoreCase} duplicate-code check already live, so a row that collides with
 * an existing (or an earlier-in-this-file) product code surfaces as the exact same {@code
 * ApiException} message a manual create would give, just caught per row and turned into a
 * skipped-row entry instead of a failed HTTP request. A CSV {@code category}/{@code brand} column
 * is looked up by name and auto-created if it doesn't exist yet — deliberate convenience for
 * loading a brand-new catalog that doesn't have any categories set up yet (unlike a typo'd {@code
 * taxRateCode}, which just leaves the product tax-exempt rather than creating anything, since
 * tax rates are a much smaller, more deliberately curated list — see Admin &gt; Settings &gt;
 * Taxes).
 * <p>
 * {@link #importOpeningStock} deliberately does *not* go through {@code
 * CatalogService.adjustStock} — that's the low-stock-alert chokepoint (see that method's own
 * javadoc), and a bulk initial stock load for a brand-new store is exactly the kind of mass
 * stock-quantity change that must never fire a flood of "low stock" emails. It sets {@code
 * stockQuantity} directly instead, the same way {@code DataSeeder} seeds opening stock for the
 * demo catalog.
 */
@Service
public class BulkImportService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final TaxRateRepository taxRateRepository;
    private final CustomerRepository customerRepository;
    private final CatalogService catalogService;

    public BulkImportService(ProductRepository productRepository, CategoryRepository categoryRepository,
                              BrandRepository brandRepository, TaxRateRepository taxRateRepository,
                              CustomerRepository customerRepository, CatalogService catalogService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.taxRateRepository = taxRateRepository;
        this.customerRepository = customerRepository;
        this.catalogService = catalogService;
    }

    /** Columns (case-insensitive, any order): name, sku, barcode, code, unitPrice, costPrice, stockQuantity, trackStock, active, category, brand, taxRateCode, unspCode, service. Only name/unitPrice are required. */
    @Transactional
    public ImportResultDto importProducts(MultipartFile file) {
        int imported = 0;
        List<SkippedRowDto> skippedRows = new ArrayList<>();
        int rowNumber = 0;
        for (Map<String, String> row : readRows(file)) {
            rowNumber++;
            try {
                ProductRequest request = new ProductRequest();
                request.setName(requireText(row, "name"));
                request.setSku(text(row, "sku"));
                request.setBarcode(text(row, "barcode"));
                request.setCode(text(row, "code"));
                request.setUnitPrice(requireDecimal(row, "unitPrice"));
                request.setCostPrice(decimalOrNull(row, "costPrice"));
                request.setStockQuantity(intOrDefault(row, "stockQuantity", 0));
                request.setTrackStock(boolOrDefault(row, "trackStock", true));
                request.setActive(boolOrDefault(row, "active", true));
                request.setService(boolOrDefault(row, "service", false));
                request.setUnspCode(text(row, "unspCode"));
                request.setCategoryId(categoryIdOrCreate(text(row, "category")));
                request.setBrandId(brandIdOrCreate(text(row, "brand")));
                request.setTaxRateId(taxRateIdOrNull(text(row, "taxRateCode")));

                catalogService.createProduct(request);
                imported++;
            } catch (ApiException ex) {
                skippedRows.add(new SkippedRowDto(rowNumber, ex.getMessage()));
            } catch (RuntimeException ex) {
                skippedRows.add(new SkippedRowDto(rowNumber, "Unexpected error: " + ex.getMessage()));
            }
        }
        return new ImportResultDto(imported, skippedRows.size(), skippedRows);
    }

    /** Columns: name, phone, email, taxId, creditEnabled, creditLimit, defaultCreditTermDays. Only name is required. A row matching an existing (or earlier-in-this-file) customer on name+phone, case-insensitive, is skipped rather than creating a duplicate. */
    @Transactional
    public ImportResultDto importCustomers(MultipartFile file) {
        List<Customer> seenThisBatch = new ArrayList<>(customerRepository.findAll());
        int imported = 0;
        List<SkippedRowDto> skippedRows = new ArrayList<>();
        int rowNumber = 0;
        for (Map<String, String> row : readRows(file)) {
            rowNumber++;
            try {
                String name = requireText(row, "name");
                String phone = text(row, "phone");
                boolean duplicate = seenThisBatch.stream().anyMatch(c ->
                        c.getName().equalsIgnoreCase(name) && java.util.Objects.equals(normalizedOrNull(c.getPhone()), normalizedOrNull(phone)));
                if (duplicate) {
                    throw ApiException.conflict("A customer named '" + name + "' with the same phone already exists");
                }
                Customer customer = new Customer();
                customer.setName(name);
                customer.setPhone(phone);
                customer.setEmail(text(row, "email"));
                customer.setTaxId(text(row, "taxId"));
                customer.setCreditEnabled(boolOrDefault(row, "creditEnabled", false));
                customer.setCreditLimit(decimalOrDefault(row, "creditLimit", BigDecimal.ZERO));
                customer.setDefaultCreditTermDays(intOrNull(row, "defaultCreditTermDays"));
                Customer saved = customerRepository.save(customer);
                seenThisBatch.add(saved);
                imported++;
            } catch (ApiException ex) {
                skippedRows.add(new SkippedRowDto(rowNumber, ex.getMessage()));
            } catch (RuntimeException ex) {
                skippedRows.add(new SkippedRowDto(rowNumber, "Unexpected error: " + ex.getMessage()));
            }
        }
        return new ImportResultDto(imported, skippedRows.size(), skippedRows);
    }

    /** Columns: code, sku, openingQuantity. At least one of code/sku (tried in that order) must match an existing product; openingQuantity is required. Sets stockQuantity directly — see this class's own javadoc for why it bypasses the usual adjust-stock path. */
    @Transactional
    public ImportResultDto importOpeningStock(MultipartFile file) {
        int imported = 0;
        List<SkippedRowDto> skippedRows = new ArrayList<>();
        int rowNumber = 0;
        for (Map<String, String> row : readRows(file)) {
            rowNumber++;
            try {
                int quantity = requireInt(row, "openingQuantity");
                String code = text(row, "code");
                String sku = text(row, "sku");
                Optional<Product> byCode = code != null ? productRepository.findByCodeIgnoreCase(code) : Optional.<Product>empty();
                Optional<Product> bySku = sku != null ? productRepository.findBySkuIgnoreCase(sku) : Optional.<Product>empty();
                Product product = byCode.or(() -> bySku).orElseThrow(() -> ApiException.notFound("No product found for code/sku on this row"));
                product.setStockQuantity(quantity);
                productRepository.save(product);
                imported++;
            } catch (ApiException ex) {
                skippedRows.add(new SkippedRowDto(rowNumber, ex.getMessage()));
            } catch (RuntimeException ex) {
                skippedRows.add(new SkippedRowDto(rowNumber, "Unexpected error: " + ex.getMessage()));
            }
        }
        return new ImportResultDto(imported, skippedRows.size(), skippedRows);
    }

    // ---- downloadable templates (Fourier: "a button to download a template to upload") ----
    // One hard-coded example-filled CSV per importer, byte-for-byte what an admin should expect
    // back out of their own spreadsheet tool once they fill it in — see DataImportExportController
    // for the "Download template" endpoints that serve these, and this class's own javadoc above
    // for what each column means/defaults to.

    public byte[] productsTemplateCsv() {
        return (
                "name,sku,barcode,code,unitPrice,costPrice,stockQuantity,trackStock,active,category,brand,taxRateCode,unspCode,service\n" +
                "Coca-Cola 500ml,COKE-500,6009001234567,BEV-001,1500,1000,120,true,true,Beverages,Coca-Cola,VAT,,false\n" +
                "Rice 5kg,RICE-5KG,6009007654321,GRN-010,25000,20000,40,true,true,Groceries,,VAT,,false\n" +
                "Haircut (Standard),,,,10000,0,0,false,true,Services,,,,true\n"
        ).getBytes(StandardCharsets.UTF_8);
    }

    public byte[] customersTemplateCsv() {
        return (
                "name,phone,email,taxId,creditEnabled,creditLimit,defaultCreditTermDays\n" +
                "Jane Mukasa,0772123456,jane.mukasa@example.com,,false,0,\n" +
                "Masindi Traders Ltd,0772987654,accounts@masinditraders.co.ug,TIN-10029384,true,2000000,30\n" +
                "John Okello,0700112233,,,false,0,\n"
        ).getBytes(StandardCharsets.UTF_8);
    }

    public byte[] openingStockTemplateCsv() {
        return (
                "code,sku,openingQuantity\n" +
                "BEV-001,COKE-500,120\n" +
                "GRN-010,RICE-5KG,40\n" +
                ",SOAP-BAR-01,75\n"
        ).getBytes(StandardCharsets.UTF_8);
    }

    // ---- shared CSV reading ----

    private List<Map<String, String>> readRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a CSV file to import");
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                rows.add(record.toMap());
            }
            return rows;
        } catch (IOException e) {
            throw ApiException.badRequest("Failed to read the uploaded CSV file: " + e.getMessage());
        }
    }

    // ---- per-cell helpers ----

    private String text(Map<String, String> row, String column) {
        String value = row.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireText(Map<String, String> row, String column) {
        String value = text(row, column);
        if (value == null) {
            throw ApiException.badRequest(column + " is required");
        }
        return value;
    }

    private BigDecimal requireDecimal(Map<String, String> row, String column) {
        String value = text(row, column);
        if (value == null) {
            throw ApiException.badRequest(column + " is required");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(column + " is not a valid number: '" + value + "'");
        }
    }

    private BigDecimal decimalOrNull(Map<String, String> row, String column) {
        String value = text(row, column);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(column + " is not a valid number: '" + value + "'");
        }
    }

    private BigDecimal decimalOrDefault(Map<String, String> row, String column, BigDecimal fallback) {
        BigDecimal value = decimalOrNull(row, column);
        return value != null ? value : fallback;
    }

    private int intOrDefault(Map<String, String> row, String column, int fallback) {
        Integer value = intOrNull(row, column);
        return value != null ? value : fallback;
    }

    private int requireInt(Map<String, String> row, String column) {
        Integer value = intOrNull(row, column);
        if (value == null) {
            throw ApiException.badRequest(column + " is required");
        }
        return value;
    }

    private Integer intOrNull(Map<String, String> row, String column) {
        String value = text(row, column);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(column + " is not a valid whole number: '" + value + "'");
        }
    }

    private boolean boolOrDefault(Map<String, String> row, String column, boolean fallback) {
        String value = text(row, column);
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toLowerCase()) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw ApiException.badRequest(column + " is not a valid true/false value: '" + value + "'");
        };
    }

    private String normalizedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    /** Default color for a category auto-created from a CSV import — an admin can always recolor it afterward from Admin > Inventory > Categories, same as any other category. */
    private static final String DEFAULT_IMPORTED_CATEGORY_COLOR = "#6c757d";

    private Long categoryIdOrCreate(String name) {
        if (name == null) {
            return null;
        }
        return categoryRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName(name);
                    category.setColorHex(DEFAULT_IMPORTED_CATEGORY_COLOR);
                    return categoryRepository.save(category);
                })
                .getId();
    }

    private Long brandIdOrCreate(String name) {
        if (name == null) {
            return null;
        }
        return brandRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Brand brand = new Brand();
                    brand.setName(name);
                    return brandRepository.save(brand);
                })
                .getId();
    }

    /** Unlike category/brand, an unmatched tax rate code is never auto-created — it just leaves the product tax-exempt (taxRateId null), same as a product manually created with no tax rate chosen. */
    private Long taxRateIdOrNull(String code) {
        if (code == null) {
            return null;
        }
        return taxRateRepository.findByCodeIgnoreCase(code).map(t -> t.getId()).orElse(null);
    }
}
