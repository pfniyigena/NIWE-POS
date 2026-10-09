package com.posapp.invoice;

import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.credit.dto.RecordInvoicePaymentRequest;
import com.posapp.email.EmailDocumentRequest;
import com.posapp.invoice.dto.CancelPaidInvoiceRequest;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.invoice.dto.VoidInvoiceRequest;
import com.posapp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-only throughout, same tier as Sale Orders. */
@RestController
@RequestMapping("/api/invoices")
@PreAuthorize("hasRole('ADMIN')")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /** Fourier: "on the customer ... view button where we can have button to view the list of invoices, payments etc" — optional {@code ?customerId=} narrows this to one customer; see InvoiceService.listAll's javadoc. */
    @GetMapping
    public List<InvoiceDto> listAll(@RequestParam(required = false) Long customerId) {
        return invoiceService.listAll(customerId);
    }

    @GetMapping("/{id}")
    public InvoiceDto getById(@PathVariable Long id) {
        return invoiceService.getById(id);
    }

    /** Fourier: "Add credit note Menu (Void) Where we can printout them" — see InvoiceService.listCreditNotes's javadoc. Declared before "/{id}" only for readability; Spring resolves this literal path ahead of the path-variable one regardless of declaration order. */
    @GetMapping("/credit-notes")
    public List<InvoiceDto> listCreditNotes() {
        return invoiceService.listCreditNotes();
    }

    /** See InvoiceService.creditNotePdf's javadoc. */
    @GetMapping("/{id}/credit-note-pdf")
    public ResponseEntity<byte[]> creditNotePdf(@PathVariable Long id) {
        byte[] pdf = invoiceService.creditNotePdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    /** 204 No Content (which Angular's HttpClient surfaces as a null body) rather than 404 when none exists yet — see InvoiceService.getBySalesOrderIdOrNull's javadoc; the Sales Order detail screen uses this to decide "Generate invoice" vs "View invoice." */
    @GetMapping("/by-sales-order/{salesOrderId}")
    public ResponseEntity<InvoiceDto> getBySalesOrder(@PathVariable Long salesOrderId) {
        InvoiceDto dto = invoiceService.getBySalesOrderIdOrNull(salesOrderId);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.noContent().build();
    }

    @PostMapping("/from-sales-order/{salesOrderId}")
    public InvoiceDto generate(@PathVariable Long salesOrderId) {
        return invoiceService.generate(salesOrderId);
    }

    /** Fourier: "someone can create invoice (with invoice lines) without creating sales order" — a brand-new standalone invoice, starting as DRAFT. */
    @PostMapping
    public InvoiceDto createDraft(@Valid @RequestBody InvoiceRequest request) {
        return invoiceService.createDraft(request);
    }

    /** Only while DRAFT — see InvoiceService.updateDraft's javadoc. */
    @PutMapping("/{id}")
    public InvoiceDto updateDraft(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.updateDraft(id, request);
    }

    /** DRAFT -&gt; ISSUED — see InvoiceService.issue's javadoc. */
    @PostMapping("/{id}/issue")
    public InvoiceDto issue(@PathVariable Long id) {
        return invoiceService.issue(id);
    }

    /** Only while DRAFT — a draft was never a real document, see InvoiceService.deleteDraft's javadoc. */
    @DeleteMapping("/{id}")
    public void deleteDraft(@PathVariable Long id) {
        invoiceService.deleteDraft(id);
    }

    @PostMapping("/{id}/void")
    public InvoiceDto voidInvoice(@PathVariable Long id, @RequestBody VoidInvoiceRequest request) {
        return invoiceService.voidInvoice(id, request.getReason());
    }

    /** Fourier: "CANCEL/CREDIT PAID INVOICE → Create Credit Note → Handle the payment → Refund OR → Customer Credit" — see InvoiceService.cancelPaidInvoice's javadoc. */
    @PostMapping("/{id}/cancel-paid")
    public InvoiceDto cancelPaidInvoice(@PathVariable Long id, @Valid @RequestBody CancelPaidInvoiceRequest request) {
        return invoiceService.cancelPaidInvoice(id, request);
    }

    /** Fourier: "sending other documents like ... invoices ... to the customer's email" — see InvoiceService.emailToCustomer's javadoc. */
    @PostMapping("/{id}/email")
    public void email(@PathVariable Long id, @RequestBody(required = false) EmailDocumentRequest request) {
        invoiceService.emailToCustomer(id, request != null ? request.getEmail() : null);
    }

    /** Fourier: "add print (PDF with A4)" — see InvoiceService.pdf's javadoc. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = invoiceService.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    /** Fourier: "Let record payment per invoice not per customer (all outstanding invoices)" — see InvoiceService.recordPayment's javadoc. */
    @PostMapping("/{id}/payments")
    public CustomerPaymentDto recordPayment(@PathVariable Long id,
                                             @Valid @RequestBody RecordInvoicePaymentRequest request,
                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        return invoiceService.recordPayment(id, request, principal.getUser());
    }

    /** This invoice's own payment history — see InvoiceService.paymentHistory's javadoc. */
    @GetMapping("/{id}/payments")
    public List<CustomerPaymentDto> paymentHistory(@PathVariable Long id) {
        return invoiceService.paymentHistory(id);
    }
}
