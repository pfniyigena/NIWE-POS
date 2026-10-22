package com.posapp.inventory;

import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.posapp.common.ListPaging;
import com.posapp.inventory.dto.SupplierDto;
import com.posapp.inventory.dto.SupplierRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-only throughout — same tier as Products/Categories, since only the "new purchase order" form (also admin-only) ever needs a supplier. */
@RestController
@RequestMapping("/api/suppliers")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_ANY')")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public ResponseEntity<List<SupplierDto>> listAll(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        List<SupplierDto> rows = supplierService.listAll().stream()
                .filter(x -> ListPaging.has(q, x.name(), x.contactName(), x.phone(), x.email(), x.taxId()))
                .filter(x -> active == null || active == x.active())
                .toList();
        return ListPaging.of(rows, page, size);
    }

    /** Powers the purchase-order form's supplier picker — active suppliers only. */
    @GetMapping("/active")
    public List<SupplierDto> listActive() {
        return supplierService.listActive();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping
    public SupplierDto create(@Valid @RequestBody SupplierRequest request) {
        return supplierService.create(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PutMapping("/{id}")
    public SupplierDto update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        supplierService.delete(id);
    }
}
