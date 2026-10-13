package com.posapp.inventory;

import com.posapp.inventory.dto.ProcessPurchaseReturnRequest;
import com.posapp.inventory.dto.PurchaseReturnDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin-only, same tier as Purchase Orders themselves. */
@RestController
@RequestMapping("/api/purchase-returns")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
public class PurchaseReturnController {

    private final PurchaseReturnService purchaseReturnService;

    public PurchaseReturnController(PurchaseReturnService purchaseReturnService) {
        this.purchaseReturnService = purchaseReturnService;
    }

    /** Store-wide Purchase Returns list screen. */
    @GetMapping
    public List<PurchaseReturnDto> listAll() {
        return purchaseReturnService.listAll();
    }

    @GetMapping("/{id}")
    public PurchaseReturnDto getById(@PathVariable Long id) {
        return purchaseReturnService.getById(id);
    }

    /** The Purchase Order detail screen's own return history section. */
    @GetMapping("/order/{purchaseOrderId}")
    public List<PurchaseReturnDto> listForOrder(@PathVariable Long purchaseOrderId) {
        return purchaseReturnService.listForOrder(purchaseOrderId);
    }

    /** Keyed by PurchaseOrderLine id; a line with nothing returned against it is absent, not present with a zero. */
    @GetMapping("/order/{purchaseOrderId}/quantities")
    public Map<Long, Integer> returnedQuantitiesForOrder(@PathVariable Long purchaseOrderId) {
        return purchaseReturnService.returnedQuantitiesForOrder(purchaseOrderId);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping
    public PurchaseReturnDto processReturn(@Valid @RequestBody ProcessPurchaseReturnRequest request) {
        return purchaseReturnService.processReturn(request);
    }
}
