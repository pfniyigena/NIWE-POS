package com.posapp.dataimport;

import com.posapp.dataimport.dto.ImportResultDto;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin > Data > Import / Export / Year-end archive (Fourier: "no backup/restore or bulk
 * import-export anywhere in the backend"). ADMIN-only end to end, same tier as Reports/Backups —
 * see {@link BulkImportService}, {@link DataExportService}, and {@link YearEndArchiveService}
 * for what each endpoint actually does.
 */
@RestController
@RequestMapping("/api/data")
@PreAuthorize("hasRole('ADMIN')")
public class DataImportExportController {

    private final BulkImportService bulkImportService;
    private final MasterImportService masterImportService;
    private final OpeningBalanceImportService openingBalanceImportService;
    private final LedgerBalanceImportService ledgerBalanceImportService;
    private final DataExportService dataExportService;
    private final YearEndArchiveService yearEndArchiveService;
    private final com.posapp.audit.AuditLogService auditLogService;

    public DataImportExportController(BulkImportService bulkImportService, MasterImportService masterImportService, OpeningBalanceImportService openingBalanceImportService, LedgerBalanceImportService ledgerBalanceImportService,
                                       DataExportService dataExportService, YearEndArchiveService yearEndArchiveService,
                                       com.posapp.audit.AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        this.masterImportService = masterImportService;
        this.openingBalanceImportService = openingBalanceImportService;
        this.ledgerBalanceImportService = ledgerBalanceImportService;
        this.bulkImportService = bulkImportService;
        this.dataExportService = dataExportService;
        this.yearEndArchiveService = yearEndArchiveService;
    }

    /** Fourier: "a button to download a template to upload" — a filled-in example CSV with the exact columns importProducts expects, so there's no guessing at the right header row. */
    @GetMapping("/import/products/template")
    public ResponseEntity<byte[]> productsTemplate() {
        return csvDownload("products-import-template.csv", bulkImportService.productsTemplateCsv());
    }

    @PostMapping(value = "/import/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importProducts(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun) {
        return audited("products", dryRun, bulkImportService.importProducts(file, dryRun));
    }

    @GetMapping("/import/customers/template")
    public ResponseEntity<byte[]> customersTemplate() {
        return csvDownload("customers-import-template.csv", bulkImportService.customersTemplateCsv());
    }

    @PostMapping(value = "/import/customers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importCustomers(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun) {
        return audited("customers", dryRun, bulkImportService.importCustomers(file, dryRun));
    }

    @GetMapping("/import/opening-stock/template")
    public ResponseEntity<byte[]> openingStockTemplate() {
        return csvDownload("opening-stock-import-template.csv", bulkImportService.openingStockTemplateCsv());
    }

    @PostMapping(value = "/import/opening-stock", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importOpeningStock(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun) {
        return audited("opening stock", dryRun, bulkImportService.importOpeningStock(file, dryRun));
    }

    /** {@code dryRun=true}: read the file and report exactly what would happen, saving nothing. */
    @PostMapping(value = "/import/suppliers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importSuppliers(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun) {
        return audited("suppliers", dryRun, masterImportService.importSuppliers(file, dryRun));
    }

    @GetMapping("/import/suppliers/template")
    public ResponseEntity<byte[]> suppliersTemplate() {
        return csvDownload("suppliers-import-template.csv", masterImportService.suppliersTemplateCsv());
    }

    @PostMapping(value = "/import/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importCategories(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun) {
        return audited("categories", dryRun, masterImportService.importCategories(file, dryRun));
    }

    @GetMapping("/import/categories/template")
    public ResponseEntity<byte[]> categoriesTemplate() {
        return csvDownload("categories-import-template.csv", masterImportService.categoriesTemplateCsv());
    }

    @PostMapping(value = "/import/brands", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importBrands(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun) {
        return audited("brands", dryRun, masterImportService.importBrands(file, dryRun));
    }

    @GetMapping("/import/brands/template")
    public ResponseEntity<byte[]> brandsTemplate() {
        return csvDownload("brands-import-template.csv", masterImportService.brandsTemplateCsv());
    }

    @PostMapping(value = "/import/customer-balances", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importCustomerBalances(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun) {
        return audited("customer balances", dryRun, openingBalanceImportService.importCustomerBalances(file, dryRun));
    }

    @GetMapping("/import/customer-balances/template")
    public ResponseEntity<byte[]> customerBalancesTemplate() {
        return csvDownload("customer-balances-import-template.csv", openingBalanceImportService.customerBalancesTemplateCsv());
    }

    @PostMapping(value = "/import/supplier-balances", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importSupplierBalances(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun) {
        return audited("supplier balances", dryRun, openingBalanceImportService.importSupplierBalances(file, dryRun));
    }

    @GetMapping("/import/supplier-balances/template")
    public ResponseEntity<byte[]> supplierBalancesTemplate() {
        return csvDownload("supplier-balances-import-template.csv", openingBalanceImportService.supplierBalancesTemplateCsv());
    }

    @PostMapping(value = "/import/ledger-balances", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importLedgerBalances(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun,
                                                @RequestParam(defaultValue = "false") boolean balanceToEquity) {
        return audited("ledger balances", dryRun, ledgerBalanceImportService.importBalances(file, dryRun, balanceToEquity));
    }

    @GetMapping("/import/ledger-balances/template")
    public ResponseEntity<byte[]> ledgerBalancesTemplate() {
        return csvDownload("ledger-balances-import-template.csv", ledgerBalanceImportService.templateCsv());
    }

    /** v1.14.0: a real (not dry-run) import is recorded in the Audit Log, with how many rows went in and how many were skipped. */
    private ImportResultDto audited(String what, boolean dryRun, ImportResultDto result) {
        if (!dryRun && result != null) {
            auditLogService.record("DATA_IMPORTED", "Imported " + what + ": " + result.imported() + " row(s) imported, " + result.skipped() + " skipped");
        }
        return result;
    }

    @GetMapping("/export/products")
    public ResponseEntity<byte[]> exportProducts() {
        auditLogService.record("DATA_EXPORTED", "Exported the product list");
        return xlsxDownload("products.xlsx", dataExportService.exportProductsXlsx());
    }

    @GetMapping("/export/customers")
    public ResponseEntity<byte[]> exportCustomers() {
        auditLogService.record("DATA_EXPORTED", "Exported the customer list");
        return xlsxDownload("customers.xlsx", dataExportService.exportCustomersXlsx());
    }

    @GetMapping("/export/year-end")
    public ResponseEntity<byte[]> exportYearEnd(@RequestParam int year) {
        byte[] content = yearEndArchiveService.exportYear(year);
        auditLogService.record("YEAR_END_EXPORTED", "Downloaded the year-end archive for " + year);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename("year-end-" + year + ".zip").build().toString())
                .body(content);
    }

    private ResponseEntity<byte[]> xlsxDownload(String filename, byte[] content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(content);
    }

    private ResponseEntity<byte[]> csvDownload(String filename, byte[] content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(content);
    }
}
