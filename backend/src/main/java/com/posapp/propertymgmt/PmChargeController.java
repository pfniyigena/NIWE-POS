package com.posapp.propertymgmt;

import com.posapp.propertymgmt.dto.PropertyMgmtChargeDtos.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** {@code /api/propertymgmt}: utilities and service charges. Operations viewers read; Operations managers and admins change. */
@RestController
@RequestMapping("/api/propertymgmt")
public class PmChargeController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final PmChargeService service;

    public PmChargeController(PmChargeService service) {
        this.service = service;
    }

    @GetMapping("/charge-types")
    @PreAuthorize(VIEW)
    public List<ChargeTypeDto> types(@RequestParam(required = false) Boolean activeOnly) {
        return service.listTypes(Boolean.TRUE.equals(activeOnly));
    }

    @PostMapping("/charge-types")
    @PreAuthorize(MANAGE)
    public ChargeTypeDto createType(@RequestBody ChargeTypeRequest r) {
        return service.createType(r);
    }

    @PutMapping("/charge-types/{id}")
    @PreAuthorize(MANAGE)
    public ChargeTypeDto updateType(@PathVariable Long id, @RequestBody ChargeTypeRequest r) {
        return service.updateType(id, r);
    }

    @DeleteMapping("/charge-types/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> deleteType(@PathVariable Long id) {
        service.deleteType(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/leases/{leaseId}/charges")
    @PreAuthorize(VIEW)
    public LeaseChargesView get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long leaseId) {
        return service.get(PropertyManagementController.actor(me), leaseId);
    }

    @PostMapping("/leases/{leaseId}/charges")
    @PreAuthorize(MANAGE)
    public LeaseChargesView add(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long leaseId, @RequestBody LeaseChargeRequest r) {
        return service.addLeaseCharge(PropertyManagementController.actor(me), leaseId, r);
    }

    @PutMapping("/lease-charges/{id}")
    @PreAuthorize(MANAGE)
    public LeaseChargesView update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody LeaseChargeRequest r) {
        return service.updateLeaseCharge(PropertyManagementController.actor(me), id, r);
    }

    @DeleteMapping("/lease-charges/{id}")
    @PreAuthorize(MANAGE)
    public LeaseChargesView remove(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.removeLeaseCharge(PropertyManagementController.actor(me), id);
    }

    @PostMapping("/lease-charges/{id}/readings")
    @PreAuthorize(MANAGE)
    public LeaseChargesView reading(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ReadingRequest r) {
        return service.addReading(PropertyManagementController.actor(me), id, r);
    }

    @PostMapping("/leases/{leaseId}/extras")
    @PreAuthorize(MANAGE)
    public LeaseChargesView extra(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long leaseId, @RequestBody ExtraRequest r) {
        return service.addExtra(PropertyManagementController.actor(me), leaseId, r);
    }

    @DeleteMapping("/extras/{id}")
    @PreAuthorize(MANAGE)
    public LeaseChargesView cancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.cancelExtra(PropertyManagementController.actor(me), id);
    }
}
