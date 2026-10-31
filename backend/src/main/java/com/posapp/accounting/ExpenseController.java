package com.posapp.accounting;

import com.posapp.accounting.dto.ExpenseDto;
import com.posapp.accounting.dto.ExpenseExtras.AttachmentDto;
import com.posapp.accounting.dto.ExpenseExtras.BulkRequest;
import com.posapp.accounting.dto.ExpenseExtras.BulkResult;
import com.posapp.accounting.dto.ExpenseExtras.EventDto;
import com.posapp.accounting.dto.ExpenseExtras.PayRequest;
import com.posapp.accounting.dto.ExpenseExtras.PaymentAccountDto;
import com.posapp.accounting.dto.ExpenseExtras.PaymentDto;
import com.posapp.accounting.dto.ExpenseExtras.SummaryDto;
import com.posapp.accounting.dto.ExpenseRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Logging, editing, paying and files need Accounting manage; approving, rejecting and voiding need {@code Permission.APPROVE_EXPENSE}.
 * What is above the approval limit is checked in {@link ExpenseService#approve}.
 */
@RestController
@RequestMapping("/api/accounting/expenses")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_VIEW')")
public class ExpenseController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_MANAGE')";
    private static final String APPROVE = "hasRole('ADMIN') or hasAuthority('PERM_APPROVE_EXPENSE')";

    private final ExpenseService expenseService;
    private final ExpenseBulkService bulkService;
    private final ExpenseAttachmentService attachmentService;
    private final ExpenseOverviewService overviewService;

    public ExpenseController(ExpenseService expenseService, ExpenseBulkService bulkService, ExpenseAttachmentService attachmentService, ExpenseOverviewService overviewService) {
        this.expenseService = expenseService;
        this.bulkService = bulkService;
        this.attachmentService = attachmentService;
        this.overviewService = overviewService;
    }

    /** Optional filters: status, accountId (category), costCenterId, branchId, q (payee, memo, number, reference), from, to, minAmount, maxAmount, paid, view (pending, unpaid, overdue, due-soon, mine). */
    @GetMapping
    public List<ExpenseDto> list(@RequestParam(required = false) String status, @RequestParam(required = false) Long accountId,
                                 @RequestParam(required = false) Long costCenterId, @RequestParam(required = false) Long branchId,
                                 @RequestParam(required = false) String q, @RequestParam(required = false) LocalDate from,
                                 @RequestParam(required = false) LocalDate to, @RequestParam(required = false) BigDecimal minAmount,
                                 @RequestParam(required = false) BigDecimal maxAmount, @RequestParam(required = false) Boolean paid,
                                 @RequestParam(required = false) String view) {
        return expenseService.list(new ExpenseService.Filter(status, accountId, costCenterId, q, from, to, minAmount, maxAmount, paid, view, branchId));
    }

    @GetMapping("/payment-accounts")
    public List<PaymentAccountDto> paymentAccounts() {
        return expenseService.paymentAccounts();
    }

    @GetMapping("/summary")
    public SummaryDto summary() {
        return overviewService.summary();
    }

    @GetMapping("/{id}")
    public ExpenseDto getById(@PathVariable Long id) {
        return expenseService.getById(id);
    }

    @GetMapping("/{id}/payments")
    public List<PaymentDto> payments(@PathVariable Long id) {
        return expenseService.payments(id);
    }

    @GetMapping("/{id}/events")
    public List<EventDto> events(@PathVariable Long id) {
        return expenseService.events(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping
    public ExpenseDto create(@Valid @RequestBody ExpenseRequest request) {
        return expenseService.create(request);
    }

    /** Only while PENDING — see ExpenseService.update. */
    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public ExpenseDto update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(id, request);
    }

    /** Only a pending or rejected expense — see ExpenseService.delete. */
    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        expenseService.delete(id);
    }

    /** Moves a PENDING expense to APPROVED, which is what posts it. {@code overrideBudget} (administrators only) goes ahead when the budget block is on. */
    @PreAuthorize(APPROVE)
    @PostMapping("/{id}/approve")
    public ExpenseDto approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        boolean override = body != null && Boolean.TRUE.equals(body.get("overrideBudget"));
        return expenseService.approve(id, override);
    }

    @PreAuthorize(APPROVE)
    @PostMapping("/{id}/reject")
    public ExpenseDto reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return expenseService.reject(id, body != null ? body.get("reason") : null);
    }

    @PreAuthorize(APPROVE)
    @PostMapping("/{id}/void")
    public ExpenseDto voidExpense(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return expenseService.voidExpense(id, body != null ? body.get("reason") : null);
    }

    /** Approve or reject several pending expenses at once. */
    @PreAuthorize(APPROVE)
    @PostMapping("/bulk")
    public BulkResult bulk(@RequestBody BulkRequest request) {
        return bulkService.run(request);
    }

    /** Pays the rest of an approved unpaid bill. */
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/settle")
    public ExpenseDto settle(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        LocalDate settledDate = body != null && body.get("settledDate") != null ? LocalDate.parse(body.get("settledDate")) : null;
        return expenseService.settle(id, settledDate);
    }

    /** A full or part payment on an approved unpaid bill. */
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/pay")
    public ExpenseDto pay(@PathVariable Long id, @RequestBody PayRequest request) {
        return expenseService.pay(id, request);
    }

    @GetMapping("/{id}/files")
    public List<AttachmentDto> files(@PathVariable Long id) {
        return attachmentService.list(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentDto addFile(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        return attachmentService.add(id, file);
    }

    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<byte[]> file(@PathVariable Long id, @PathVariable Long fileId) {
        ExpenseAttachmentService.FileData f = attachmentService.file(id, fileId);
        boolean inline = f.contentType().startsWith("image/") || f.contentType().equals("application/pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(f.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.data());
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/files/{fileId}")
    public void deleteFile(@PathVariable Long id, @PathVariable Long fileId) {
        attachmentService.delete(id, fileId);
    }
}
