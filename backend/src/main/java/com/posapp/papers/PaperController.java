package com.posapp.papers;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The four papers that travel with goods or a clipboard, as A4 PDFs. Each needs the same access as the screen it is printed from. */
@RestController
public class PaperController {

    private final PaperService service;

    public PaperController(PaperService service) {
        this.service = service;
    }

    private static ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(name).build().toString()).body(bytes);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW') or hasAuthority('AREA_INVENTORY_VIEW')")
    @GetMapping("/api/sales-orders/{id}/delivery-note")
    public ResponseEntity<byte[]> deliveryNote(@PathVariable Long id) {
        return pdf(service.deliveryNote(id), "delivery-note-" + id + ".pdf");
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
    @GetMapping("/api/purchase-orders/{id}/goods-received-note")
    public ResponseEntity<byte[]> goodsReceived(@PathVariable Long id) {
        return pdf(service.goodsReceived(id), "goods-received-note-" + id + ".pdf");
    }

    /** {@code showSystem}: also print the quantity the system expects (off by default so nobody counts towards a number). */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
    @GetMapping("/api/stock-counts/{id}/sheet")
    public ResponseEntity<byte[]> countSheet(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean showSystem) {
        return pdf(service.countSheet(id, showSystem), "stock-count-" + id + ".pdf");
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
    @GetMapping("/api/stock-transfers/{id}/note")
    public ResponseEntity<byte[]> transferNote(@PathVariable Long id) {
        return pdf(service.transferNote(id), "stock-transfer-" + id + ".pdf");
    }
}
