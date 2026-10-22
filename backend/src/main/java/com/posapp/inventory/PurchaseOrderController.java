package com.posapp.inventory;

import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.posapp.branch.BranchService;
import com.posapp.common.ListPaging;
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
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final BillService billService;

    private final BranchService branchService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService, BillService billService, BranchService branchService) {
        this.branchService = branchService;
        this.purchaseOrderService = purchaseOrderService;
        this.billService = billService;
    }

    @GetMapping
    public ResponseEntity<List<PurchaseOrderDto>> listAll(@RequestParam(required = false) Long supplierId, @RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) Long branchId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Long hq = branchId == null ? null : branchService.headquartersId();
        List<PurchaseOrderDto> rows = purchaseOrderService.listAll().stream()
                .filter(x -> ListPaging.has(q, x.orderNumber(), x.supplierName(), x.billNumber()))
                .filter(x -> ListPaging.is(status, x.status()))
                .filter(x -> ListPaging.sameId(supplierId, x.supplierId()))
                .filter(x -> ListPaging.branch(x.branchId(), branchId, hq))
                .filter(x -> ListPaging.between(x.orderDate(), from, to))
                .toList();
        return ListPaging.of(rows, page, size);
    }

    @GetMapping("/{id}")
    public PurchaseOrderDto getById(@PathVariable Long id) {
        return purchaseOrderService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping
    public PurchaseOrderDto create(@Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.createDraft(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PutMapping("/{id}")
    public PurchaseOrderDto update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.updateDraft(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/{id}/send")
    public PurchaseOrderDto send(@PathVariable Long id) {
        return purchaseOrderService.send(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/{id}/cancel")
    public PurchaseOrderDto cancel(@PathVariable Long id) {
        return purchaseOrderService.cancel(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
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
