package com.posapp.pricing;
import com.posapp.common.Qty;
import java.math.BigDecimal;

import com.posapp.dataimport.PriceListImportService;
import com.posapp.dataimport.dto.ImportResultDto;
import com.posapp.common.ListPaging;
import com.posapp.pricing.PricingDtos.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** {@code /api/pricing}: price lists, their prices, customer groups, and "what would this customer pay". Seeing needs the Sales area; changing needs Sales management (admin always). */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')";

    private final PricingService service;
    private final PriceListImportService importer;

    public PricingController(PricingService service, PriceListImportService importer) {
        this.service = service;
        this.importer = importer;
    }

    // ---- price lists ----

    @PreAuthorize(VIEW)
    @GetMapping("/price-lists")
    public List<ListDto> lists() {
        return service.lists();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/price-lists/{id}")
    public ListDto list(@PathVariable Long id) {
        return service.list(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/price-lists")
    public ListDto create(@RequestBody ListRequest r) {
        return service.createList(r);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/price-lists/{id}")
    public ListDto update(@PathVariable Long id, @RequestBody ListRequest r) {
        return service.updateList(id, r);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/price-lists/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteList(id);
    }

    // ---- prices on a list ----

    @PreAuthorize(VIEW)
    @GetMapping("/price-lists/{id}/entries")
    public ResponseEntity<List<EntryDto>> entries(@PathVariable Long id, @RequestParam(required = false) String q, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(service.entries(id, q), page, size);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/price-lists/{id}/entries")
    public EntryDto saveEntry(@PathVariable Long id, @RequestBody EntryRequest r) {
        return service.saveEntry(id, r);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/price-lists/{id}/entries/{entryId}")
    public void deleteEntry(@PathVariable Long id, @PathVariable Long entryId) {
        service.deleteEntry(id, entryId);
    }

    @PreAuthorize(MANAGE)
    @PostMapping(value = "/price-lists/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importEntries(@PathVariable Long id, @RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun) {
        return importer.importEntries(id, file, dryRun);
    }

    @PreAuthorize(MANAGE)
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return csv("price-list-import-template.csv", PriceListImportService.templateCsv());
    }

    @PreAuthorize(VIEW)
    @GetMapping("/price-lists/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        StringBuilder sb = new StringBuilder("sku,name,minQuantity,price\n");
        for (EntryDto e : service.entries(id, null)) {
            sb.append(cell(e.sku())).append(',').append(cell(e.productName())).append(',').append(Qty.str(e.minQuantity())).append(',').append(e.price().toPlainString()).append('\n');
        }
        return csv("price-list-" + id + ".csv", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String cell(String v) {
        if (v == null) return "";
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private static ResponseEntity<byte[]> csv(String filename, byte[] content) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString()).body(content);
    }

    // ---- customer groups ----

    @PreAuthorize(VIEW)
    @GetMapping("/groups")
    public List<GroupDto> groups() {
        return service.groups();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/groups")
    public GroupDto createGroup(@RequestBody GroupRequest r) {
        return service.createGroup(r);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/groups/{id}")
    public GroupDto updateGroup(@PathVariable Long id, @RequestBody GroupRequest r) {
        return service.updateGroup(id, r);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/groups/{id}")
    public void deleteGroup(@PathVariable Long id) {
        service.deleteGroup(id);
    }

    /** Puts customers in a group, or (groupId null) takes them out of any group. */
    @PreAuthorize(MANAGE)
    @PostMapping("/assign")
    public java.util.Map<String, Integer> assign(@RequestBody AssignRequest r) {
        return java.util.Map.of("changed", service.assign(r.customerIds(), r.groupId()));
    }

    // ---- what would they pay ----

    @PreAuthorize(VIEW)
    @GetMapping("/resolve")
    public Resolved resolve(@RequestParam(required = false) Long customerId, @RequestParam Long productId, @RequestParam(defaultValue = "1") BigDecimal quantity) {
        return service.resolve(customerId, productId, quantity);
    }
}
