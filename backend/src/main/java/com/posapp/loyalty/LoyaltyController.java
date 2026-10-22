package com.posapp.loyalty;

import com.posapp.loyalty.LoyaltyDtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** {@code /api/loyalty}: the till reads a customer's balance; the back office sets the rules and adjusts points. */
@RestController
@RequestMapping("/api/loyalty")
public class LoyaltyController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')";

    private final LoyaltyService service;

    public LoyaltyController(LoyaltyService service) {
        this.service = service;
    }

    /** The till needs these rules to show what points are worth, so any signed-in user may read them. */
    @GetMapping("/settings")
    public Settings settings() {
        return service.getSettings();
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/settings")
    public Settings update(@RequestBody Settings r) {
        return service.updateSettings(r);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/summary")
    public Summary summary() {
        return service.summary();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/customers")
    public ResponseEntity<List<CustomerRow>> customers(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return com.posapp.common.ListPaging.of(service.customers(q), page, size);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/customers/{id}")
    public CustomerDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/customers/{id}/adjust")
    public CustomerDetail adjust(@PathVariable Long id, @RequestBody AdjustRequest r) {
        return service.adjust(id, r);
    }

    /** The till: a customer's points and what they are worth. */
    @GetMapping("/balance/{customerId}")
    public Balance balance(@PathVariable Long customerId) {
        return service.balance(customerId);
    }
}
