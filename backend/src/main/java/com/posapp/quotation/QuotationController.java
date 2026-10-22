package com.posapp.quotation;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.posapp.branch.BranchService;
import com.posapp.common.ListPaging;
import com.posapp.email.EmailDocumentRequest;
import com.posapp.quotation.dto.QuotationDto;
import com.posapp.quotation.dto.QuotationRequest;
import com.posapp.salesorder.dto.SalesOrderDto;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-only throughout, same tier as Sale Orders/Purchase Orders — quoting a customer is a back-office action, not a terminal one. */
@RestController
@RequestMapping("/api/quotations")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW')")
public class QuotationController {

    private final QuotationService quotationService;

    private final BranchService branchService;

    public QuotationController(QuotationService quotationService, BranchService branchService) {
        this.branchService = branchService;
        this.quotationService = quotationService;
    }

    @GetMapping
    public ResponseEntity<List<QuotationDto>> listAll(@RequestParam(required = false) Long customerId, @RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) Long branchId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Long hq = branchId == null ? null : branchService.headquartersId();
        List<QuotationDto> rows = quotationService.listAll().stream()
                .filter(x -> ListPaging.has(q, x.quoteNumber(), x.customerName()))
                .filter(x -> ListPaging.is(status, x.status()))
                .filter(x -> ListPaging.sameId(customerId, x.customerId()))
                .filter(x -> ListPaging.branch(x.branchId(), branchId, hq))
                .filter(x -> ListPaging.between(x.quoteDate(), from, to))
                .toList();
        return ListPaging.of(rows, page, size);
    }

    @GetMapping("/{id}")
    public QuotationDto getById(@PathVariable Long id) {
        return quotationService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping
    public QuotationDto create(@Valid @RequestBody QuotationRequest request) {
        return quotationService.createDraft(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PutMapping("/{id}")
    public QuotationDto update(@PathVariable Long id, @Valid @RequestBody QuotationRequest request) {
        return quotationService.updateDraft(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping("/{id}/send")
    public QuotationDto send(@PathVariable Long id) {
        return quotationService.send(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping("/{id}/accept")
    public QuotationDto accept(@PathVariable Long id) {
        return quotationService.accept(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping("/{id}/reject")
    public QuotationDto reject(@PathVariable Long id) {
        return quotationService.reject(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping("/{id}/expire")
    public QuotationDto expire(@PathVariable Long id) {
        return quotationService.expire(id);
    }

    /** Returns the newly-created Sales Order (not the quotation) so the frontend can navigate straight to it — see QuotationService.convertToSalesOrder's javadoc. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping("/{id}/convert")
    public SalesOrderDto convert(@PathVariable Long id) {
        return quotationService.convertToSalesOrder(id);
    }

    /** Fourier: "sending other documents like quotations ... to the customer's email" — see QuotationService.emailToCustomer's javadoc. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping("/{id}/email")
    public void email(@PathVariable Long id, @RequestBody(required = false) EmailDocumentRequest request) {
        quotationService.emailToCustomer(id, request != null ? request.getEmail() : null);
    }

    /** Fourier: "Add print A4 on Quotation and Label it as proforma" — see QuotationService.pdf's javadoc. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = quotationService.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
