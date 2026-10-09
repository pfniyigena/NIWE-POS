package com.posapp.bill;

import com.posapp.bill.dto.BillDto;
import com.posapp.bill.dto.VoidBillRequest;
import com.posapp.inventory.dto.RecordBillPaymentRequest;
import com.posapp.inventory.dto.SupplierPaymentDto;
import com.posapp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Fourier: "same workflow as Sale order" — admin-only throughout, same tier as Purchase Orders/Invoices. */
@RestController
@RequestMapping("/api/bills")
@PreAuthorize("hasRole('ADMIN')")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    /** Optional {@code ?supplierId=} narrows this to one supplier; see BillService.listAll's javadoc. */
    @GetMapping
    public List<BillDto> listAll(@RequestParam(required = false) Long supplierId) {
        return billService.listAll(supplierId);
    }

    @GetMapping("/{id}")
    public BillDto getById(@PathVariable Long id) {
        return billService.getById(id);
    }

    /** Fourier: "Refunds equivalent with Credit note" — see BillService.listRefunds's javadoc. Declared before "/{id}" only for readability; Spring resolves this literal path ahead of the path-variable one regardless of declaration order. */
    @GetMapping("/refunds")
    public List<BillDto> listRefunds() {
        return billService.listRefunds();
    }

    /** See BillService.refundPdf's javadoc. */
    @GetMapping("/{id}/refund-pdf")
    public ResponseEntity<byte[]> refundPdf(@PathVariable Long id) {
        byte[] pdf = billService.refundPdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    /** 204 No Content (Angular's HttpClient surfaces that as a null body) rather than 404 when none exists yet — see BillService.getByPurchaseOrderIdOrNull's javadoc; the Purchase Order detail screen uses this to decide "Generate bill" vs "View bill." */
    @GetMapping("/by-purchase-order/{purchaseOrderId}")
    public ResponseEntity<BillDto> getByPurchaseOrder(@PathVariable Long purchaseOrderId) {
        BillDto dto = billService.getByPurchaseOrderIdOrNull(purchaseOrderId);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.noContent().build();
    }

    @PostMapping("/from-purchase-order/{purchaseOrderId}")
    public BillDto generate(@PathVariable Long purchaseOrderId) {
        return billService.generate(purchaseOrderId);
    }

    /** DRAFT -&gt; ISSUED — see BillService.issue's javadoc. */
    @PostMapping("/{id}/issue")
    public BillDto issue(@PathVariable Long id) {
        return billService.issue(id);
    }

    /** Only while DRAFT — a draft was never a real document, see BillService.deleteDraft's javadoc. */
    @DeleteMapping("/{id}")
    public void deleteDraft(@PathVariable Long id) {
        billService.deleteDraft(id);
    }

    @PostMapping("/{id}/void")
    public BillDto voidBill(@PathVariable Long id, @RequestBody VoidBillRequest request) {
        return billService.voidBill(id, request.getReason());
    }

    /** See BillService.pdf's javadoc. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = billService.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    /** Fourier: "same workflow as Sale order" — see BillService.recordPayment's javadoc. */
    @PostMapping("/{id}/payments")
    public SupplierPaymentDto recordPayment(@PathVariable Long id,
                                             @Valid @RequestBody RecordBillPaymentRequest request,
                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        return billService.recordPayment(id, request, principal.getUser());
    }

    /** This bill's own payment history — see BillService.paymentHistory's javadoc. */
    @GetMapping("/{id}/payments")
    public List<SupplierPaymentDto> paymentHistory(@PathVariable Long id) {
        return billService.paymentHistory(id);
    }
}
