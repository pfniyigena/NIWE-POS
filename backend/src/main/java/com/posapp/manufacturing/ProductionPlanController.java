package com.posapp.manufacturing;

import com.posapp.manufacturing.dto.GenerateOrderFromPlanLineRequest;
import com.posapp.manufacturing.dto.GenerateProductionPlanRequest;
import com.posapp.manufacturing.dto.ManufacturingOrderDto;
import com.posapp.manufacturing.dto.ProductionPlanDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturing/production-plans")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class ProductionPlanController {

    private final ProductionPlanService productionPlanService;

    public ProductionPlanController(ProductionPlanService productionPlanService) {
        this.productionPlanService = productionPlanService;
    }

    @GetMapping
    public org.springframework.http.ResponseEntity<List<ProductionPlanDto>> listAll(@RequestParam(required = false) String q, @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from, @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return com.posapp.common.ListPaging.of(productionPlanService.filtered(q, from, to), page, size);
    }

    @GetMapping("/{id}")
    public ProductionPlanDto getById(@PathVariable Long id) {
        return productionPlanService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/generate")
    public ProductionPlanDto generate(@Valid @RequestBody GenerateProductionPlanRequest request) {
        return productionPlanService.generate(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{planId}/lines/{lineId}/generate-order")
    public ManufacturingOrderDto generateOrder(@PathVariable Long planId, @PathVariable Long lineId,
                                                @Valid @RequestBody GenerateOrderFromPlanLineRequest request) {
        return productionPlanService.generateManufacturingOrder(planId, lineId, request);
    }
}
