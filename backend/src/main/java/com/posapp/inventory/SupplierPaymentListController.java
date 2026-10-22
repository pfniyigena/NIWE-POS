package com.posapp.inventory;

import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.posapp.common.ListPaging;
import com.posapp.inventory.dto.SupplierPaymentDto;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Store-wide supplier payment history, across every supplier — separate from {@link
 * SupplierPaymentController} (which is scoped to one supplier under {@code
 * /api/suppliers/{supplierId}}) the same way the new store-wide Customer Payments list screen is
 * separate from the per-customer {@code CustomerCreditController}. Backs the Supplier Payments
 * list reached from Admin > Inventory > Suppliers.
 *
 * <p>Fourier: "Add view details on supplier payments list" — {@code /{id}} and {@code
 * /{id}/pdf} mirror {@code CustomerPaymentController}'s own reprint-by-id endpoints exactly.
 */
@RestController
public class SupplierPaymentListController {

    private final SupplierPaymentService supplierPaymentService;

    public SupplierPaymentListController(SupplierPaymentService supplierPaymentService) {
        this.supplierPaymentService = supplierPaymentService;
    }

    @GetMapping("/api/supplier-payments")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_VIEW')")
    public ResponseEntity<List<SupplierPaymentDto>> allPayments(@RequestParam(required = false) String q,
            @RequestParam(required = false) String method, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        List<SupplierPaymentDto> rows = supplierPaymentService.paymentHistory().stream()
                .filter(x -> ListPaging.has(q, x.supplierName(), x.receiptNumber(), x.note()))
                .filter(x -> ListPaging.is(method, x.method()))
                .filter(x -> ListPaging.sameId(supplierId, x.supplierId()))
                .filter(x -> ListPaging.between(x.paidAt(), from, to))
                .toList();
        return ListPaging.of(rows, page, size);
    }

    @GetMapping("/api/supplier-payments/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_VIEW')")
    public SupplierPaymentDto getReceipt(@PathVariable Long id) {
        return supplierPaymentService.getPaymentReceipt(id);
    }

    @GetMapping("/api/supplier-payments/{id}/pdf")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_VIEW')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = supplierPaymentService.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
