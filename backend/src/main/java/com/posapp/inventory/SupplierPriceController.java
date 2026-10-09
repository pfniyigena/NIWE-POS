package com.posapp.inventory;

import com.posapp.inventory.dto.SupplierPriceDto;
import com.posapp.inventory.dto.SupplierPriceRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supplier-prices")
@PreAuthorize("hasRole('ADMIN')")
public class SupplierPriceController {

    private final SupplierPriceService service;

    public SupplierPriceController(SupplierPriceService service) {
        this.service = service;
    }

    @GetMapping
    public List<SupplierPriceDto> list(@RequestParam(required = false) Long supplierId) {
        return service.list(supplierId);
    }

    /** The prices in force today for one supplier — used to prefill purchase order lines. */
    @GetMapping("/current")
    public List<SupplierPriceDto> current(@RequestParam Long supplierId) {
        return service.currentForSupplier(supplierId);
    }

    @PostMapping
    public SupplierPriceDto create(@Valid @RequestBody SupplierPriceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SupplierPriceDto update(@PathVariable Long id, @Valid @RequestBody SupplierPriceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
