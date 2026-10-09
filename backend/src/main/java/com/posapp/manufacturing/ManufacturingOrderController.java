package com.posapp.manufacturing;

import com.posapp.manufacturing.dto.CreateManufacturingOrderRequest;
import com.posapp.manufacturing.dto.ManufacturingOrderDto;
import com.posapp.manufacturing.dto.ProduceManufacturingOrderRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturing/orders")
@PreAuthorize("hasRole('ADMIN')")
public class ManufacturingOrderController {

    private final ManufacturingOrderService manufacturingOrderService;

    public ManufacturingOrderController(ManufacturingOrderService manufacturingOrderService) {
        this.manufacturingOrderService = manufacturingOrderService;
    }

    @GetMapping
    public List<ManufacturingOrderDto> listAll() {
        return manufacturingOrderService.listAll();
    }

    @GetMapping("/{id}")
    public ManufacturingOrderDto getById(@PathVariable Long id) {
        return manufacturingOrderService.getById(id);
    }

    @PostMapping
    public ManufacturingOrderDto create(@Valid @RequestBody CreateManufacturingOrderRequest request) {
        return manufacturingOrderService.create(request);
    }

    @PostMapping("/{id}/confirm")
    public ManufacturingOrderDto confirm(@PathVariable Long id) {
        return manufacturingOrderService.confirm(id);
    }

    @PostMapping("/{id}/produce")
    public ManufacturingOrderDto produce(@PathVariable Long id, @Valid @RequestBody ProduceManufacturingOrderRequest request) {
        return manufacturingOrderService.produce(id, request);
    }

    @PostMapping("/{id}/cancel")
    public ManufacturingOrderDto cancel(@PathVariable Long id) {
        return manufacturingOrderService.cancel(id);
    }
}
