package com.posapp.customer;

import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.posapp.common.ListPaging;
import com.posapp.common.ApiException;
import com.posapp.customer.dto.CustomerDto;
import com.posapp.customer.dto.CustomerRequest;
import com.posapp.security.AppUserPrincipal;
import com.posapp.user.Role;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Task: "Cashier can add or modify customer" / "Give Cashier Customer menu" — create/update used
 * to be {@code @PreAuthorize("hasRole('ADMIN')")}, but the terminal's own inline "Add customer"
 * panel (an earlier task: "Add Customer button from Terminal screen") has always called this same
 * {@code create} endpoint, so a cashier using that panel was silently getting a 403 the whole
 * time — this was never actually reachable for a cashier despite the terminal UI offering it. Now
 * open to any logged-in user, same as {@code list}, but the store-credit fields stay ADMIN-only
 * (see {@link #applyBasicFields}/{@link #applyCreditFields} below) — that split is what keeps
 * this consistent with the separate, deliberate "Only Admin can enable Customer Credit" rule.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final com.posapp.audit.AuditLogService auditLogService;

    public CustomerController(CustomerRepository customerRepository, com.posapp.audit.AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        this.customerRepository = customerRepository;
    }

    /** Open to any logged-in user — the terminal's customer picker needs this list. */
    @GetMapping
    public ResponseEntity<List<CustomerDto>> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean credit,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        List<CustomerDto> rows = customerRepository.findAll().stream().map(CustomerDto::from)
                .filter(x -> ListPaging.has(q, x.name(), x.phone(), x.email(), x.taxId()))
                .filter(x -> credit == null || credit == x.creditEnabled())
                .toList();
        return ListPaging.of(rows, page, size);
    }

    /** Fourier: "add one button (View) on customer then put all others buttons on view form" — backs CustomerDetailComponent's own single-record load, same convention as every other module's detail page (SalesOrderController/InvoiceController getById), rather than fetching the whole list and finding one client-side. Open to any logged-in user, same as {@link #list}. */
    @GetMapping("/{id}")
    public CustomerDto getById(@PathVariable Long id) {
        return CustomerDto.from(customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + id)));
    }

    /** Any logged-in user — see the class doc comment above. Credit fields are ignored for a non-admin caller (a brand-new customer they create simply starts with credit off, same as the entity's own default). */
    @PostMapping
    public CustomerDto create(@Valid @RequestBody CustomerRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        Customer customer = new Customer();
        applyBasicFields(customer, request);
        if (principal.getUser().canManage(com.posapp.user.AccessArea.SALES)) {
            applyCreditFields(customer, request);
        }
        Customer saved = customerRepository.save(customer);
        auditLogService.record("CUSTOMER_CREATED", "Added customer " + saved.getName(), "Customer", saved.getId());
        return CustomerDto.from(saved);
    }

    /** Any logged-in user — see the class doc comment above. A non-admin's request simply can't touch this customer's existing credit fields, whatever it sends for them. */
    @PutMapping("/{id}")
    public CustomerDto update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + id));
        boolean wasCredit = customer.isCreditEnabled();
        java.math.BigDecimal wasLimit = customer.getCreditLimit();
        applyBasicFields(customer, request);
        if (principal.getUser().canManage(com.posapp.user.AccessArea.SALES)) {
            applyCreditFields(customer, request);
        }
        Customer saved = customerRepository.save(customer);
        String credit = saved.isCreditEnabled() != wasCredit ? "; credit " + (saved.isCreditEnabled() ? "switched on" : "switched off")
                : (saved.getCreditLimit() != null && wasLimit != null && saved.getCreditLimit().compareTo(wasLimit) != 0 ? "; credit limit " + wasLimit.toPlainString() + " -> " + saved.getCreditLimit().toPlainString() : "");
        auditLogService.record("CUSTOMER_UPDATED", "Updated customer " + saved.getName() + credit, "Customer", saved.getId());
        return CustomerDto.from(saved);
    }

    private void applyBasicFields(Customer customer, CustomerRequest request) {
        customer.setName(request.getName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setTaxId(request.getTaxId());
    }

    /** Store-credit fields (Task: "Only Admin can enable Customer Credit") — only ever called for an ADMIN caller, see create/update above. */
    private void applyCreditFields(Customer customer, CustomerRequest request) {
        customer.setCreditEnabled(request.isCreditEnabled());
        customer.setCreditLimit(request.getCreditLimit() != null ? request.getCreditLimit() : java.math.BigDecimal.ZERO);
        customer.setDefaultCreditTermDays(request.getDefaultCreditTermDays());
    }
}
