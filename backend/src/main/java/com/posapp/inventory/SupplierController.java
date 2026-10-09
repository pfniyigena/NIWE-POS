package com.posapp.inventory;

import com.posapp.inventory.dto.SupplierDto;
import com.posapp.inventory.dto.SupplierRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-only throughout — same tier as Products/Categories, since only the "new purchase order" form (also admin-only) ever needs a supplier. */
@RestController
@RequestMapping("/api/suppliers")
@PreAuthorize("hasRole('ADMIN')")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public List<SupplierDto> listAll() {
        return supplierService.listAll();
    }

    /** Powers the purchase-order form's supplier picker — active suppliers only. */
    @GetMapping("/active")
    public List<SupplierDto> listActive() {
        return supplierService.listActive();
    }

    @PostMapping
    public SupplierDto create(@Valid @RequestBody SupplierRequest request) {
        return supplierService.create(request);
    }

    @PutMapping("/{id}")
    public SupplierDto update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        supplierService.delete(id);
    }
}
