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
    public org.springframework.http.ResponseEntity<List<BomDto>> listAll(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) Long productId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return com.posapp.common.ListPaging.of(bomService.filtered(q, status, productId), page, size);
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
    @PostMapping("/{id}/copy")
    public BomDto copy(@PathVariable Long id) {
        return bomService.copy(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/activate")
    public BomDto activate(@PathVariable Long id) {
        return bomService.activate(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping("/{id}")
    public BomDto update(@PathVariable Long id, @Valid @RequestBody BomRequest request) {
        return bomService.update(id, request);
    }
}
