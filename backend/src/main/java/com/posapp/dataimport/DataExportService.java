package com.posapp.dataimport;

import com.posapp.catalog.ProductRepository;
import com.posapp.catalog.dto.ProductDto;
import com.posapp.customer.CustomerRepository;
import com.posapp.customer.dto.CustomerDto;
import com.posapp.reports.ReportExportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Fourier: "no ... bulk import-export anywhere in the backend ... for the owner's own records, or
 * migrating elsewhere." Deliberately reuses {@link ReportExportService#toXlsx} rather than
 * building its own workbook writer — every POS Report already produces an .xlsx this exact way
 * (title + header row + string rows), and a product/customer list export is the same shape of
 * table, just not filtered by a date range.
 */
@Service
public class DataExportService {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ReportExportService exportService;

    public DataExportService(ProductRepository productRepository, CustomerRepository customerRepository,
                              ReportExportService exportService) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.exportService = exportService;
    }

    @Transactional(readOnly = true)
    public byte[] exportProductsXlsx() {
        List<String> headers = List.of("Name", "SKU", "Barcode", "Code", "Category", "Brand", "Unit price",
                "Cost price", "Stock qty", "Track stock", "Tax rate", "Active", "Service");
        List<List<String>> rows = new ArrayList<>();
        for (ProductDto p : productRepository.findAll().stream().map(ProductDto::from).toList()) {
            rows.add(List.of(
                    p.name(), orDash(p.sku()), orDash(p.barcode()), orDash(p.code()),
                    orDash(p.categoryName()), orDash(p.brandName()), p.unitPrice().toPlainString(),
                    p.costPrice() == null ? "0.00" : p.costPrice().toPlainString(),
                    String.valueOf(p.stockQuantity()), String.valueOf(p.trackStock()),
                    orDash(p.taxRateCode()), String.valueOf(p.active()), String.valueOf(p.service())
            ));
        }
        return exportService.toXlsx("Products", headers, rows);
    }

    @Transactional(readOnly = true)
    public byte[] exportCustomersXlsx() {
        List<String> headers = List.of("Name", "Phone", "Email", "Tax ID", "Credit enabled", "Credit limit", "Default credit term (days)");
        List<List<String>> rows = new ArrayList<>();
        for (CustomerDto c : customerRepository.findAll().stream().map(CustomerDto::from).toList()) {
            rows.add(List.of(
                    c.name(), orDash(c.phone()), orDash(c.email()), orDash(c.taxId()),
                    String.valueOf(c.creditEnabled()), c.creditLimit() == null ? "0.00" : c.creditLimit().toPlainString(),
                    c.defaultCreditTermDays() == null ? "—" : String.valueOf(c.defaultCreditTermDays())
            ));
        }
        return exportService.toXlsx("Customers", headers, rows);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
