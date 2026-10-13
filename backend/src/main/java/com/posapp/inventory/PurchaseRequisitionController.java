package com.posapp.inventory;

import com.posapp.inventory.dto.ConvertRequisitionRequest;
import com.posapp.inventory.dto.PurchaseOrderDto;
import com.posapp.inventory.dto.PurchaseRequisitionDto;
import com.posapp.inventory.dto.PurchaseRequisitionRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only throughout, same tier as Purchase Orders/Suppliers — except {@link #approve}/
 * {@link #reject} below, which Fourier's "Build a full permissions system" additionally opens to
 * anyone granted {@code Permission.APPROVE_PURCHASE_REQUISITION}, via their own method-level
 * {@code @PreAuthorize} overriding this class-level one.
 */
@RestController
@RequestMapping("/api/purchase-requisitions")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
public class PurchaseRequisitionController {

    private final PurchaseRequisitionService purchaseRequisitionService;

    public PurchaseRequisitionController(PurchaseRequisitionService purchaseRequisitionService) {
        this.purchaseRequisitionService = purchaseRequisitionService;
    }

    @GetMapping
    public List<PurchaseRequisitionDto> listAll() {
        return purchaseRequisitionService.listAll();
    }

    @GetMapping("/{id}")
    public PurchaseRequisitionDto getById(@PathVariable Long id) {
        return purchaseRequisitionService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping
    public PurchaseRequisitionDto create(@Valid @RequestBody PurchaseRequisitionRequest request) {
        return purchaseRequisitionService.createDraft(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PutMapping("/{id}")
    public PurchaseRequisitionDto update(@PathVariable Long id, @Valid @RequestBody PurchaseRequisitionRequest request) {
        return purchaseRequisitionService.updateDraft(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/{id}/submit")
    public PurchaseRequisitionDto submit(@PathVariable Long id) {
        return purchaseRequisitionService.submit(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_PURCHASE_REQUISITION')")
    @PostMapping("/{id}/approve")
    public PurchaseRequisitionDto approve(@PathVariable Long id) {
        return purchaseRequisitionService.approve(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_PURCHASE_REQUISITION')")
    @PostMapping("/{id}/reject")
    public PurchaseRequisitionDto reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return purchaseRequisitionService.reject(id, body.get("reason"));
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/{id}/cancel")
    public PurchaseRequisitionDto cancel(@PathVariable Long id) {
        return purchaseRequisitionService.cancel(id);
    }

    /** Returns the newly-created Purchase Order (not the requisition) so the frontend can navigate straight to it — see PurchaseRequisitionService.convertToPurchaseOrder's javadoc. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/{id}/convert")
    public PurchaseOrderDto convert(@PathVariable Long id, @Valid @RequestBody ConvertRequisitionRequest request) {
        return purchaseRequisitionService.convertToPurchaseOrder(id, request);
    }
}
