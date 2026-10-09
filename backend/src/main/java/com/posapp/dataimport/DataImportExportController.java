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
    private final DataExportService dataExportService;
    private final YearEndArchiveService yearEndArchiveService;

    public DataImportExportController(BulkImportService bulkImportService, DataExportService dataExportService,
                                       YearEndArchiveService yearEndArchiveService) {
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
    public ImportResultDto importProducts(@RequestParam("file") MultipartFile file) {
        return bulkImportService.importProducts(file);
    }

    @GetMapping("/import/customers/template")
    public ResponseEntity<byte[]> customersTemplate() {
        return csvDownload("customers-import-template.csv", bulkImportService.customersTemplateCsv());
    }

    @PostMapping(value = "/import/customers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importCustomers(@RequestParam("file") MultipartFile file) {
        return bulkImportService.importCustomers(file);
    }

    @GetMapping("/import/opening-stock/template")
    public ResponseEntity<byte[]> openingStockTemplate() {
        return csvDownload("opening-stock-import-template.csv", bulkImportService.openingStockTemplateCsv());
    }

    @PostMapping(value = "/import/opening-stock", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importOpeningStock(@RequestParam("file") MultipartFile file) {
        return bulkImportService.importOpeningStock(file);
    }

    @GetMapping("/export/products")
    public ResponseEntity<byte[]> exportProducts() {
        return xlsxDownload("products.xlsx", dataExportService.exportProductsXlsx());
    }

    @GetMapping("/export/customers")
    public ResponseEntity<byte[]> exportCustomers() {
        return xlsxDownload("customers.xlsx", dataExportService.exportCustomersXlsx());
    }

    @GetMapping("/export/year-end")
    public ResponseEntity<byte[]> exportYearEnd(@RequestParam int year) {
        byte[] content = yearEndArchiveService.exportYear(year);
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
