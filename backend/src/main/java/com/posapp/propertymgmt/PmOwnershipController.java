package com.posapp.propertymgmt;

import com.posapp.propertymgmt.dto.PropertyMgmtOwnershipDtos.OwnershipRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtOwnershipDtos.OwnershipView;
import com.posapp.security.AppUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** {@code /api/propertymgmt/properties/{id}/owners}: who owns a property and the share of each. Operations viewers read; Operations managers and admins change. */
@RestController
@RequestMapping("/api/propertymgmt/properties/{propertyId}/owners")
public class PmOwnershipController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final PmOwnershipService service;

    public PmOwnershipController(PmOwnershipService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    public OwnershipView get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long propertyId) {
        return service.get(PropertyManagementController.actor(me), propertyId);
    }

    /** Sets the owners from a date (add a split, or replace the one that starts that day). */
    @PutMapping
    @PreAuthorize(MANAGE)
    public OwnershipView set(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long propertyId, @RequestBody OwnershipRequest r) {
        return service.set(PropertyManagementController.actor(me), propertyId, r);
    }

    /** Removes the split that starts on the date in {@code key} (yyyy-MM-dd), or "start" for the one from the beginning. */
    @DeleteMapping("/{key}")
    @PreAuthorize(MANAGE)
    public OwnershipView remove(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long propertyId, @PathVariable String key) {
        return service.remove(PropertyManagementController.actor(me), propertyId, key);
    }
}
