package com.posapp.propertymgmt;

import com.posapp.propertymgmt.dto.PropertyMgmtDepositDtos.*;
import com.posapp.reports.ReportExportService;
import com.posapp.security.AppUserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** {@code /api/propertymgmt}: security deposits and inspections. Operations viewers read; Operations managers and admins add, change and delete. */
@RestController
@RequestMapping("/api/propertymgmt")
public class PmDepositController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final PmDepositService deposits;
    private final PmInspectionService inspections;
    private final ReportExportService exportService;

    public PmDepositController(PmDepositService deposits, PmInspectionService inspections, ReportExportService exportService) {
        this.deposits = deposits;
        this.inspections = inspections;
        this.exportService = exportService;
    }

    // ------------------------------------------------------------------ deposits

    @GetMapping("/deposits")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<DepositRow>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String state, @RequestParam(required = false) Long propertyId,
                                                 @RequestParam(required = false) Long ownerId, @RequestParam(required = false) Long tenantId, @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return PmPaging.of(deposits.list(q, state, propertyId, ownerId, tenantId), page, size);
    }

    @GetMapping("/deposits/totals")
    @PreAuthorize(VIEW)
    public DepositTotals totals() {
        return deposits.totals();
    }

    @GetMapping("/leases/{id}/deposit")
    @PreAuthorize(VIEW)
    public DepositView get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return deposits.get(PropertyManagementController.actor(me), id);
    }

    @PostMapping("/leases/{id}/deposit/receive")
    @PreAuthorize(MANAGE)
    public DepositView receive(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ReceiveRequest r) {
        return deposits.receive(PropertyManagementController.actor(me), id, r);
    }

    @PostMapping("/leases/{id}/deposit/deduct")
    @PreAuthorize(MANAGE)
    public DepositView deduct(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody DeductRequest r) {
        return deposits.deduct(PropertyManagementController.actor(me), id, r);
    }

    @PostMapping("/leases/{id}/deposit/refund")
    @PreAuthorize(MANAGE)
    public DepositView refund(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody RefundRequest r) {
        return deposits.refund(PropertyManagementController.actor(me), id, r);
    }

    @PostMapping("/leases/{id}/deposit/transfer")
    @PreAuthorize(MANAGE)
    public DepositView transfer(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody TransferRequest r) {
        return deposits.transfer(PropertyManagementController.actor(me), id, r);
    }

    @PostMapping("/deposits/entries/{txnId}/void")
    @PreAuthorize(MANAGE)
    public DepositView voidEntry(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long txnId, @RequestBody(required = false) VoidRequest r) {
        return deposits.voidTxn(PropertyManagementController.actor(me), txnId, r);
    }

    // ------------------------------------------------------------------ inspections

    @GetMapping("/inspections")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<InspectionRow>> inspectionList(@RequestParam(required = false) Long leaseId, @RequestParam(required = false) PmInspectionKind kind,
                                                              @RequestParam(required = false) PmInspectionStatus status, @RequestParam(required = false) String q,
                                                              @RequestParam(required = false) Long tenantId, @RequestParam(required = false) Long propertyId,
                                                              @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PmPaging.of(inspections.list(leaseId, kind, status, q, tenantId, propertyId), page, size);
    }

    @GetMapping("/inspections/{id}")
    @PreAuthorize(VIEW)
    public InspectionDto inspection(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return inspections.get(PropertyManagementController.actor(me), id);
    }

    @PostMapping("/leases/{id}/inspections")
    @PreAuthorize(MANAGE)
    public InspectionDto startInspection(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody InspectionRequest r) {
        return inspections.create(PropertyManagementController.actor(me), id, r);
    }

    @PutMapping("/inspections/{id}")
    @PreAuthorize(MANAGE)
    public InspectionDto updateInspection(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody InspectionUpdate r) {
        return inspections.update(PropertyManagementController.actor(me), id, r);
    }

    @PostMapping("/inspections/{id}/items")
    @PreAuthorize(MANAGE)
    public InspectionDto addItem(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ItemRequest r) {
        return inspections.addItem(PropertyManagementController.actor(me), id, r);
    }

    @PutMapping("/inspections/{id}/items/{itemId}")
    @PreAuthorize(MANAGE)
    public InspectionDto updateItem(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long itemId, @RequestBody ItemRequest r) {
        return inspections.updateItem(PropertyManagementController.actor(me), id, itemId, r);
    }

    @DeleteMapping("/inspections/{id}/items/{itemId}")
    @PreAuthorize(MANAGE)
    public InspectionDto deleteItem(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long itemId) {
        return inspections.deleteItem(PropertyManagementController.actor(me), id, itemId);
    }

    @PostMapping("/inspections/{id}/sign-off")
    @PreAuthorize(MANAGE)
    public InspectionDto signOff(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) SignOffRequest r) {
        return inspections.signOff(PropertyManagementController.actor(me), id, r);
    }

    @PostMapping("/inspections/{id}/reopen")
    @PreAuthorize(MANAGE)
    public InspectionDto reopen(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return inspections.reopen(PropertyManagementController.actor(me), id);
    }

    @DeleteMapping("/inspections/{id}")
    @PreAuthorize(MANAGE)
    public void deleteInspection(@PathVariable Long id) {
        inspections.delete(id);
    }

    /** The printed list, for the tenant to sign: {@code format=pdf} (default) or {@code xlsx}. */
    @GetMapping("/inspections/{id}/print")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> print(@PathVariable Long id, @RequestParam(defaultValue = "pdf") String format) {
        PmInspectionService.Printout p = inspections.printout(id);
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        byte[] content = xlsx ? exportService.toXlsx(p.title(), p.headers(), p.rows()) : exportService.toPdf(p.title(), p.headers(), p.rows());
        String filename = p.title().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (filename.length() > 80) filename = filename.substring(0, 80);
        filename += xlsx ? ".xlsx" : ".pdf";
        return ResponseEntity.ok()
                .contentType(xlsx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(content);
    }
}
