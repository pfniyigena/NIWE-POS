package com.posapp.inventory;

import com.posapp.bill.BillService;
import com.posapp.inventory.dto.PurchaseOrderDto;
import com.posapp.inventory.dto.PurchaseOrderRequest;
import com.posapp.inventory.dto.ReceivePurchaseOrderRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only throughout, same tier as Products/Suppliers — restocking is a back-office task, not a
 * terminal one.
 *
 * <p>Fourier: "The Purchase order should flow same workflow as Sale order (with Bills ...
 * equivalent with Invoices)" — {@link #receive} is where that's wired in, at the controller layer
 * rather than inside {@code PurchaseOrderService}, for the exact same reason {@code
 * SalesOrderController.fulfill}'s own javadoc gives: {@code BillService} already depends on {@code
 * PurchaseOrderService} (to read the order via its own public {@code getById}), so the reverse
 * dependency would be circular. The {@code getByPurchaseOrderIdOrNull} check just avoids throwing
 * on a later receiving call against an order that's already fully received and billed.
 */
@RestController
@RequestMapping("/api/purchase-orders")
@PreAuthorize("hasRole('ADMIN')")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final BillService billService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService, BillService billService) {
        this.purchaseOrderService = purchaseOrderService;
        this.billService = billService;
    }

    @GetMapping
    public List<PurchaseOrderDto> listAll() {
        return purchaseOrderService.listAll();
    }

    @GetMapping("/{id}")
    public PurchaseOrderDto getById(@PathVariable Long id) {
        return purchaseOrderService.getById(id);
    }

    @PostMapping
    public PurchaseOrderDto create(@Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.createDraft(request);
    }

    @PutMapping("/{id}")
    public PurchaseOrderDto update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.updateDraft(id, request);
    }

    @PostMapping("/{id}/send")
    public PurchaseOrderDto send(@PathVariable Long id) {
        return purchaseOrderService.send(id);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseOrderDto cancel(@PathVariable Long id) {
        return purchaseOrderService.cancel(id);
    }

    @PostMapping("/{id}/receive")
    public PurchaseOrderDto receive(@PathVariable Long id, @Valid @RequestBody ReceivePurchaseOrderRequest request) {
        PurchaseOrderDto po = purchaseOrderService.receiveLines(id, request);
        // Fourier: "same workflow as Sale order" — see class javadoc.
        if ("RECEIVED".equals(po.status()) && billService.getByPurchaseOrderIdOrNull(id) == null) {
            billService.generateDraftFromPurchaseOrder(id);
            po = purchaseOrderService.getById(id);
        }
        return po;
    }
}
