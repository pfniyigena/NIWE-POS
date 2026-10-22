package com.posapp.workorder;

import com.posapp.security.AppUserPrincipal;
import com.posapp.workorder.WorkOrderService.Actor;
import com.posapp.workorder.dto.WorkOrderDtos.*;
import com.posapp.workorder.dto.WorkOrderRequests.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Work orders. Everyone signed in sees and works the jobs assigned to them ({@code /mine}, and the detail of their own jobs). Seeing every job
 * follows the Operations area (view); creating, assigning, costing, reopening and cancelling follows Operations (manage); invoicing follows
 * Billing (manage).
 */
@RestController
@RequestMapping("/api/work-orders")
@PreAuthorize("isAuthenticated()")
public class WorkOrderController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String BILL = "hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE')";

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manage = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean view = manage || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), view, manage);
    }

    // ---- lists

    @PreAuthorize(VIEW)
    @GetMapping
    public List<WorkOrderSummary> list(@RequestParam(required = false) WorkOrderStatus status, @RequestParam(required = false) WorkOrderType type,
                                       @RequestParam(required = false) Long assetId, @RequestParam(required = false) Long assigneeId,
                                       @RequestParam(required = false) Long customerId, @RequestParam(defaultValue = "false") boolean overdue,
                                       @RequestParam(required = false) String q) {
        return service.list(status, type, assetId, assigneeId, customerId, overdue, q);
    }

    @GetMapping("/mine")
    public List<WorkOrderSummary> mine(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.mine(me.getUser());
    }

    @GetMapping("/summary")
    public SummaryDto summary(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.summary(me.getUser());
    }

    // ---- service packages (declared before /{id} so the path is not read as an id)

    @PreAuthorize(VIEW)
    @GetMapping("/packages")
    public List<PackageSummary> packages(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.packages(activeOnly);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/packages/{id}")
    public PackageDetail packageDetail(@PathVariable Long id) {
        return service.packageDetail(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/packages")
    public PackageDetail createPackage(@RequestBody PackageRequest request) {
        return service.savePackage(null, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/packages/{id}")
    public PackageDetail updatePackage(@PathVariable Long id, @RequestBody PackageRequest request) {
        return service.savePackage(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/packages/{id}")
    public void deletePackage(@PathVariable Long id) {
        service.deletePackage(id);
    }

    // ---- one work order

    @GetMapping("/{id}")
    public WorkOrderDetail detail(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.detail(actor(me), id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping
    public WorkOrderDetail create(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody WorkOrderRequest request) {
        return service.create(actor(me), request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public WorkOrderDetail update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody WorkOrderRequest request) {
        return service.update(actor(me), id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        service.delete(actor(me), id);
    }

    // ---- parts, checklist, costs

    @PutMapping("/{id}/lines")
    public WorkOrderDetail lines(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody LinesRequest request) {
        return service.saveLines(actor(me), id, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/checklist")
    public WorkOrderDetail checklist(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ChecklistRequest request) {
        return service.saveChecklist(actor(me), id, request);
    }

    @PostMapping("/{id}/checklist/{itemId}/toggle")
    public WorkOrderDetail toggle(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long itemId) {
        return service.toggleChecklist(actor(me), id, itemId);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/costs")
    public WorkOrderDetail addCost(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody CostRequest request) {
        return service.addCost(actor(me), id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/costs/{costId}")
    public WorkOrderDetail deleteCost(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long costId) {
        return service.deleteCost(actor(me), id, costId);
    }

    // ---- status

    @PostMapping("/{id}/start")
    public WorkOrderDetail start(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.start(actor(me), id);
    }

    @PostMapping("/{id}/hold")
    public WorkOrderDetail hold(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.hold(actor(me), id);
    }

    @PostMapping("/{id}/complete")
    public WorkOrderDetail complete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) CompleteRequest request) {
        return service.complete(actor(me), id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/reopen")
    public WorkOrderDetail reopen(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.reopen(actor(me), id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/cancel")
    public WorkOrderDetail cancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.cancel(actor(me), id);
    }

    // ---- manufacturing

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/manufacturing-order")
    public WorkOrderDetail linkManufacturing(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ManufacturingRequest request) {
        return service.linkManufacturing(actor(me), id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/manufacturing-order")
    public WorkOrderDetail unlinkManufacturing(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.unlinkManufacturing(actor(me), id);
    }

    // ---- billing

    @PreAuthorize(BILL)
    @GetMapping("/{id}/invoice-preview")
    public InvoicePreview invoicePreview(@PathVariable Long id) {
        return service.invoicePreview(id);
    }

    @PreAuthorize(BILL)
    @PostMapping("/{id}/invoice")
    public InvoiceResult invoice(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) InvoiceRequestBody request) {
        return service.createInvoice(actor(me), id, request);
    }

    @PreAuthorize(BILL)
    @PostMapping("/{id}/release-invoice")
    public WorkOrderDetail release(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.releaseInvoice(actor(me), id);
    }
}
