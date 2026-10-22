package com.posapp.propertymgmt;

import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.AccountMailRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.MailSent;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.PaymentRow;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.Receipt;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.ReceiptMailRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.TenantAccount;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** {@code /api/propertymgmt}: a tenant's account statement, the rent payments received and rent receipts, to read, download as PDF and e-mail to the tenant. */
@RestController
@RequestMapping("/api/propertymgmt")
public class PmTenantAccountController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final PmTenantAccountService accounts;
    private final PmTenantDocuments documents;
    private final PmTenantMailService mail;

    public PmTenantAccountController(PmTenantAccountService accounts, PmTenantDocuments documents, PmTenantMailService mail) {
        this.accounts = accounts;
        this.documents = documents;
        this.mail = mail;
    }

    private static ResponseEntity<byte[]> pdf(byte[] content, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(name).build().toString()).body(content);
    }

    @GetMapping("/tenants/{id}/account")
    @PreAuthorize(VIEW)
    public TenantAccount account(@PathVariable Long id, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accounts.account(id, from, to);
    }

    @GetMapping("/tenants/{id}/account.pdf")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> accountPdf(@PathVariable Long id, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        TenantAccount a = accounts.account(id, from, to);
        return pdf(documents.statement(a, documents.currency()), "tenant-statement-" + a.tenantCode().toLowerCase() + ".pdf");
    }

    @PostMapping("/tenants/{id}/account/email")
    @PreAuthorize(MANAGE)
    public MailSent emailAccount(@PathVariable Long id, @RequestBody(required = false) AccountMailRequest r) {
        return mail.emailStatement(id, r);
    }

    @GetMapping("/payments")
    @PreAuthorize(VIEW)
    public List<PaymentRow> payments(@RequestParam(required = false) Long leaseId, @RequestParam(required = false) Long tenantId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accounts.payments(leaseId, tenantId, from, to);
    }

    @GetMapping("/payments/{id}/receipt")
    @PreAuthorize(VIEW)
    public Receipt receipt(@PathVariable Long id) {
        return accounts.receipt(id);
    }

    @GetMapping("/payments/{id}/receipt.pdf")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> receiptPdf(@PathVariable Long id) {
        Receipt r = accounts.receipt(id);
        return pdf(documents.receipt(r, documents.currency()), PmTenantMailService.fileName(r));
    }

    @PostMapping("/payments/{id}/receipt/email")
    @PreAuthorize(MANAGE)
    public MailSent emailReceipt(@PathVariable Long id, @RequestBody(required = false) ReceiptMailRequest r) {
        return mail.emailReceipt(id, r);
    }
}
