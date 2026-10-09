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
@PreAuthorize("hasRole('ADMIN')")
public class ProductionPlanController {

    private final ProductionPlanService productionPlanService;

    public ProductionPlanController(ProductionPlanService productionPlanService) {
        this.productionPlanService = productionPlanService;
    }

    @GetMapping
    public List<ProductionPlanDto> listAll() {
        return productionPlanService.listAll();
    }

    @GetMapping("/{id}")
    public ProductionPlanDto getById(@PathVariable Long id) {
        return productionPlanService.getById(id);
    }

    @PostMapping("/generate")
    public ProductionPlanDto generate(@Valid @RequestBody GenerateProductionPlanRequest request) {
        return productionPlanService.generate(request);
    }

    @PostMapping("/{planId}/lines/{lineId}/generate-order")
    public ManufacturingOrderDto generateOrder(@PathVariable Long planId, @PathVariable Long lineId,
                                                @Valid @RequestBody GenerateOrderFromPlanLineRequest request) {
        return productionPlanService.generateManufacturingOrder(planId, lineId, request);
    }
}
