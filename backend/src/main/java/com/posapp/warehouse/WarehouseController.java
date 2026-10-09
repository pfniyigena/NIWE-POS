package com.posapp.warehouse;

import com.posapp.warehouse.dto.ProductStockByLocationDto;
import com.posapp.warehouse.dto.WarehouseDto;
import com.posapp.warehouse.dto.WarehouseLocationDto;
import com.posapp.warehouse.dto.WarehouseLocationRequest;
import com.posapp.warehouse.dto.WarehouseRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin > Warehouses — ADMIN-only, same tier as every other back-office module. See Warehouse's own javadoc for the overall plan; {@link #create} is refused while Inventory settings' multi-warehouse toggle is off and a warehouse already exists — see WarehouseService#create's own javadoc. */
@RestController
@RequestMapping("/api/warehouses")
@PreAuthorize("hasRole('ADMIN')")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    public List<WarehouseDto> listAll() {
        return warehouseService.listAll();
    }

    @PostMapping
    public WarehouseDto create(@Valid @RequestBody WarehouseRequest request) {
        return warehouseService.create(request);
    }

    @PutMapping("/{id}")
    public WarehouseDto update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    /** Same convention as PackagingController.setRuleActive. */
    @PostMapping("/{id}/active")
    public WarehouseDto setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return warehouseService.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    /** Multi-warehouse phase 1: where one product's stock sits, by warehouse and location. Read-only; see WarehouseService#stockByLocation. */
    @GetMapping("/stock/product/{productId}")
    public ProductStockByLocationDto stockByLocation(@PathVariable Long productId) {
        return warehouseService.stockByLocation(productId);
    }

    // ---- Locations — sub-areas within a warehouse; see WarehouseLocation's own javadoc ----

    @GetMapping("/{warehouseId}/locations")
    public List<WarehouseLocationDto> listLocations(@PathVariable Long warehouseId) {
        return warehouseService.listLocations(warehouseId);
    }

    @PostMapping("/{warehouseId}/locations")
    public WarehouseLocationDto createLocation(@PathVariable Long warehouseId, @Valid @RequestBody WarehouseLocationRequest request) {
        return warehouseService.createLocation(warehouseId, request);
    }

    /** Same convention as #setActive, one level down. */
    @PostMapping("/locations/{id}/active")
    public WarehouseLocationDto setLocationActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return warehouseService.setLocationActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    /** Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — replaces the old set-default endpoint. Body: {"type": "RECEIVING"|"SALES"|"STORAGE"|"DAMAGED"} — see WarehouseService#setLocationType's own javadoc for the invariants this enforces. */
    @PostMapping("/locations/{id}/type")
    public WarehouseLocationDto setLocationType(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return warehouseService.setLocationType(id, body.get("type"));
    }
}
