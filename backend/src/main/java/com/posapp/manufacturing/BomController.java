package com.posapp.manufacturing;

import com.posapp.manufacturing.dto.BomDto;
import com.posapp.manufacturing.dto.BomRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturing/boms")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class BomController {

    private final BomService bomService;

    public BomController(BomService bomService) {
        this.bomService = bomService;
    }

    @GetMapping
    public List<BomDto> listAll() {
        return bomService.listAll();
    }

    @GetMapping("/product/{productId}")
    public List<BomDto> listForProduct(@PathVariable Long productId) {
        return bomService.listForProduct(productId);
    }

    @GetMapping("/{id}")
    public BomDto getById(@PathVariable Long id) {
        return bomService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping
    public BomDto create(@Valid @RequestBody BomRequest request) {
        return bomService.create(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping("/{id}")
    public BomDto update(@PathVariable Long id, @Valid @RequestBody BomRequest request) {
        return bomService.update(id, request);
    }
}
