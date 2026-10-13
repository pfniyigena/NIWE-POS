package com.posapp.manufacturing;

import com.posapp.manufacturing.dto.OverheadCostDto;
import com.posapp.manufacturing.dto.OverheadCostRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturing/overhead-costs")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class OverheadCostController {

    private final OverheadCostService overheadCostService;

    public OverheadCostController(OverheadCostService overheadCostService) {
        this.overheadCostService = overheadCostService;
    }

    @GetMapping
    public List<OverheadCostDto> listAll() {
        return overheadCostService.listAll();
    }

    @GetMapping("/active")
    public List<OverheadCostDto> listActive() {
        return overheadCostService.listActive();
    }

    @GetMapping("/{id}")
    public OverheadCostDto getById(@PathVariable Long id) {
        return overheadCostService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping
    public OverheadCostDto create(@Valid @RequestBody OverheadCostRequest request) {
        return overheadCostService.create(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping("/{id}")
    public OverheadCostDto update(@PathVariable Long id, @Valid @RequestBody OverheadCostRequest request) {
        return overheadCostService.update(id, request);
    }
}
