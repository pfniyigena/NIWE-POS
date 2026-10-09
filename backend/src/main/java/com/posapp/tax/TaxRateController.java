package com.posapp.tax;

import com.posapp.tax.dto.TaxRateDto;
import com.posapp.tax.dto.TaxRateRequest;
import com.posapp.tax.dto.UpdateTaxRateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ADMIN-only end to end — a cashier never picks a tax rate at checkout (each line's rate comes
 * from its product, see Product.taxRate), so there's no "active list for anyone logged in"
 * endpoint here the way {@code PaymentMethodController.listActive} has one; the product form
 * (also ADMIN-only) reads the same {@link #listAll} response and filters to active client-side.
 */
@RestController
@RequestMapping("/api/tax-rates")
@PreAuthorize("hasRole('ADMIN')")
public class TaxRateController {

    private final TaxRateService taxRateService;

    public TaxRateController(TaxRateService taxRateService) {
        this.taxRateService = taxRateService;
    }

    @GetMapping
    public List<TaxRateDto> listAll() {
        return taxRateService.listAll();
    }

    @PostMapping
    public TaxRateDto create(@Valid @RequestBody TaxRateRequest request) {
        return taxRateService.create(request);
    }

    @PutMapping("/{id}")
    public TaxRateDto update(@PathVariable Long id, @Valid @RequestBody UpdateTaxRateRequest request) {
        return taxRateService.update(id, request);
    }
}
