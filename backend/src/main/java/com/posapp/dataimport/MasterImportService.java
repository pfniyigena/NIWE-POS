package com.posapp.dataimport;

import com.posapp.catalog.Brand;
import com.posapp.catalog.BrandRepository;
import com.posapp.catalog.Category;
import com.posapp.catalog.CategoryRepository;
import com.posapp.common.ApiException;
import com.posapp.dataimport.dto.ImportResultDto;
import com.posapp.dataimport.dto.SkippedRowDto;
import com.posapp.inventory.Supplier;
import com.posapp.inventory.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.posapp.dataimport.ImportSupport.normalized;
import static com.posapp.dataimport.ImportSupport.requireText;
import static com.posapp.dataimport.ImportSupport.text;

/** Suppliers, categories and brands from a CSV. Same partial-success and check-first rules as the other importers. A row matching one that already exists is skipped, never duplicated. */
@Service
public class MasterImportService {

    private static final String DEFAULT_CATEGORY_COLOR = "#6c757d";

    private final SupplierRepository supplierRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public MasterImportService(SupplierRepository supplierRepository, CategoryRepository categoryRepository, BrandRepository brandRepository) {
        this.supplierRepository = supplierRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    /** Columns: name (required), contactName, phone, email, taxId, address, active. A supplier with the same name and phone (case-insensitive) already on file, or earlier in the file, is skipped. */
    @Transactional
    public ImportResultDto importSuppliers(MultipartFile file, boolean dryRun) {
        List<Map<String, String>> rows = ImportSupport.readRows(file);
        ImportSupport.requireColumns(rows, "name");
        List<Supplier> known = new ArrayList<>(supplierRepository.findAll());
        List<SkippedRowDto> skipped = new ArrayList<>();
        List<SkippedRowDto> accepted = new ArrayList<>();
        int n = 0, imported = 0;
        for (Map<String, String> row : rows) {
            n++;
            try {
                String name = requireText(row, "name");
                String phone = text(row, "phone");
                boolean duplicate = known.stream().anyMatch(s -> s.getName() != null && s.getName().equalsIgnoreCase(name) && java.util.Objects.equals(normalized(s.getPhone()), normalized(phone)));
                if (duplicate) throw ApiException.conflict("A supplier named '" + name + "' with the same phone already exists");
                Supplier s = new Supplier();
                s.setName(name);
                s.setContactName(text(row, "contactName"));
                s.setPhone(phone);
                s.setEmail(text(row, "email"));
                s.setTaxId(text(row, "taxId"));
                s.setAddress(text(row, "address"));
                s.setActive(ImportSupport.boolOrDefault(row, "active", true));
                known.add(supplierRepository.save(s));
                imported++;
                accepted.add(new SkippedRowDto(n, name + (phone == null ? "" : ", " + phone)));
            } catch (ApiException ex) {
                skipped.add(new SkippedRowDto(n, ex.getMessage()));
            } catch (RuntimeException ex) {
                skipped.add(new SkippedRowDto(n, "Unexpected error: " + ex.getMessage()));
            }
        }
        return ImportSupport.result(dryRun, n, imported, skipped, accepted, List.of());
    }

    /** Columns: name (required), colorHex (optional, like #3366cc). An existing category of that name is skipped. */
    @Transactional
    public ImportResultDto importCategories(MultipartFile file, boolean dryRun) {
        List<Map<String, String>> rows = ImportSupport.readRows(file);
        ImportSupport.requireColumns(rows, "name");
        List<SkippedRowDto> skipped = new ArrayList<>();
        List<SkippedRowDto> accepted = new ArrayList<>();
        int n = 0, imported = 0;
        for (Map<String, String> row : rows) {
            n++;
            try {
                String name = requireText(row, "name");
                String color = text(row, "colorHex");
                if (color != null && !color.matches("#[0-9a-fA-F]{6}")) throw ApiException.badRequest("colorHex must look like #3366cc, not '" + color + "'");
                if (categoryRepository.findByNameIgnoreCase(name).isPresent()) throw ApiException.conflict("A category named '" + name + "' already exists");
                Category c = new Category();
                c.setName(name);
                c.setColorHex(color != null ? color : DEFAULT_CATEGORY_COLOR);
                categoryRepository.save(c);
                imported++;
                accepted.add(new SkippedRowDto(n, name));
            } catch (ApiException ex) {
                skipped.add(new SkippedRowDto(n, ex.getMessage()));
            } catch (RuntimeException ex) {
                skipped.add(new SkippedRowDto(n, "Unexpected error: " + ex.getMessage()));
            }
        }
        return ImportSupport.result(dryRun, n, imported, skipped, accepted, List.of());
    }

    /** Column: name (required). An existing brand of that name is skipped. */
    @Transactional
    public ImportResultDto importBrands(MultipartFile file, boolean dryRun) {
        List<Map<String, String>> rows = ImportSupport.readRows(file);
        ImportSupport.requireColumns(rows, "name");
        List<SkippedRowDto> skipped = new ArrayList<>();
        List<SkippedRowDto> accepted = new ArrayList<>();
        int n = 0, imported = 0;
        for (Map<String, String> row : rows) {
            n++;
            try {
                String name = requireText(row, "name");
                if (brandRepository.findByNameIgnoreCase(name).isPresent()) throw ApiException.conflict("A brand named '" + name + "' already exists");
                Brand b = new Brand();
                b.setName(name);
                brandRepository.save(b);
                imported++;
                accepted.add(new SkippedRowDto(n, name));
            } catch (ApiException ex) {
                skipped.add(new SkippedRowDto(n, ex.getMessage()));
            } catch (RuntimeException ex) {
                skipped.add(new SkippedRowDto(n, "Unexpected error: " + ex.getMessage()));
            }
        }
        return ImportSupport.result(dryRun, n, imported, skipped, accepted, List.of());
    }

    public byte[] suppliersTemplateCsv() {
        return ("name,contactName,phone,email,taxId,address,active\n"
                + "Kampala Wholesalers Ltd,Sarah Namutebi,0772345678,orders@kampalawholesale.co.ug,TIN-20011223,Plot 12 Industrial Area,true\n"
                + "Green Farms Co-op,Peter Okello,0701987654,,,Masindi,true\n").getBytes(StandardCharsets.UTF_8);
    }

    public byte[] categoriesTemplateCsv() {
        return ("name,colorHex\nBeverages,#3366cc\nGroceries,#2e8b57\nServices,\n").getBytes(StandardCharsets.UTF_8);
    }

    public byte[] brandsTemplateCsv() {
        return ("name\nCoca-Cola\nNile Breweries\nOmo\n").getBytes(StandardCharsets.UTF_8);
    }
}
