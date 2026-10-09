package com.posapp.accounting;

import com.posapp.accounting.dto.CostCenterDto;
import com.posapp.accounting.dto.CostCenterRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounting/cost-centers")
@PreAuthorize("hasRole('ADMIN')")
public class CostCenterController {

    private final CostCenterService costCenterService;

    public CostCenterController(CostCenterService costCenterService) {
        this.costCenterService = costCenterService;
    }

    @GetMapping
    public List<CostCenterDto> listAll() {
        return costCenterService.listAll();
    }

    @GetMapping("/active")
    public List<CostCenterDto> listActive() {
        return costCenterService.listActive();
    }

    @GetMapping("/{id}")
    public CostCenterDto getById(@PathVariable Long id) {
        return costCenterService.getById(id);
    }

    @PostMapping
    public CostCenterDto create(@Valid @RequestBody CostCenterRequest request) {
        return costCenterService.create(request);
    }

    @PutMapping("/{id}")
    public CostCenterDto update(@PathVariable Long id, @Valid @RequestBody CostCenterRequest request) {
        return costCenterService.update(id, request);
    }
}
