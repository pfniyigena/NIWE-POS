package com.posapp.propertymgmt;

import com.posapp.propertymgmt.dto.PropertyMgmtFeeInvoiceDtos.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** {@code /api/propertymgmt}: the invoices to owners for the management fee. Operations viewers read and download; Operations managers and admins make the ones a statement does not have yet. */
@RestController
@RequestMapping("/api/propertymgmt")
public class PmFeeInvoiceController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final PmFeeInvoiceService service;

    public PmFeeInvoiceController(PmFeeInvoiceService service) {
        this.service = service;
    }

    @GetMapping("/fee-invoices")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<FeeInvoiceRow>> list(@RequestParam(required = false) Long ownerId, @RequestParam(required = false) PmFeeInvoiceStatus status,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) String q,
                                                    @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PmPaging.of(service.list(ownerId, status, from, to, q), page, size);
    }

    /** What the invoices in force that match the same filter add up to. */
    @GetMapping("/fee-invoices/totals")
    @PreAuthorize(VIEW)
    public FeeInvoiceTotals totals(@RequestParam(required = false) Long ownerId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) String q) {
        return PmFeeInvoiceService.totals(service.list(ownerId, null, from, to, q));
    }

    @GetMapping("/fee-invoices/{id}")
    @PreAuthorize(VIEW)
    public FeeInvoiceDto get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.get(PropertyManagementController.actor(me), id);
    }

    @GetMapping("/fee-invoices/{id}/pdf")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] bytes = service.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(service.filename(id)).build().toString()).body(bytes);
    }

    /** The invoice of a statement (the one in force, else the latest voided one); 204 when it has none. */
    @GetMapping("/statements/{id}/fee-invoice")
    @PreAuthorize(VIEW)
    public ResponseEntity<FeeInvoiceDto> forStatement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        FeeInvoiceDto d = service.forStatement(PropertyManagementController.actor(me), id);
        return d == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(d);
    }

    @PostMapping("/statements/{id}/fee-invoice")
    @PreAuthorize(MANAGE)
    public FeeInvoiceDto issue(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.issue(PropertyManagementController.actor(me), id);
    }
}
