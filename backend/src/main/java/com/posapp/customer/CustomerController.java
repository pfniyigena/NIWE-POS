package com.posapp.customer;

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

    public CustomerController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /** Open to any logged-in user — the terminal's customer picker needs this list. */
    @GetMapping
    public List<CustomerDto> list() {
        return customerRepository.findAll().stream().map(CustomerDto::from).toList();
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
        return CustomerDto.from(customerRepository.save(customer));
    }

    /** Any logged-in user — see the class doc comment above. A non-admin's request simply can't touch this customer's existing credit fields, whatever it sends for them. */
    @PutMapping("/{id}")
    public CustomerDto update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + id));
        applyBasicFields(customer, request);
        if (principal.getUser().canManage(com.posapp.user.AccessArea.SALES)) {
            applyCreditFields(customer, request);
        }
        return CustomerDto.from(customerRepository.save(customer));
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
