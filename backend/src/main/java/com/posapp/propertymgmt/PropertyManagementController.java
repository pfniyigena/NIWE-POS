package com.posapp.propertymgmt;

import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.*;
import com.posapp.propertymgmt.dto.PropertyMgmtMaintenanceDtos.*;
import com.posapp.propertymgmt.dto.PropertyMgmtRentDtos.*;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /api/propertymgmt}: owners, managed properties and their units, tenants and leases. Operations viewers read; Operations managers and admins change things; the settings are
 * for admins.
 */
@RestController
@RequestMapping("/api/propertymgmt")
public class PropertyManagementController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final PmSettingsService settingsService;
    private final PmOwnerService ownerService;
    private final PmPropertyService propertyService;
    private final PmTenantService tenantService;
    private final PmLeaseService leaseService;
    private final PmOverviewService overviewService;
    private final PmRentService rentService;
    private final PmMaintenanceService maintenanceService;

    public PropertyManagementController(PmSettingsService settingsService, PmOwnerService ownerService, PmPropertyService propertyService, PmTenantService tenantService,
                                        PmLeaseService leaseService, PmOverviewService overviewService, PmRentService rentService, PmMaintenanceService maintenanceService) {
        this.rentService = rentService;
        this.maintenanceService = maintenanceService;
        this.settingsService = settingsService;
        this.ownerService = ownerService;
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.leaseService = leaseService;
        this.overviewService = overviewService;
    }

    static Actor actor(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), admin, viewer, manager);
    }

    // ---- settings, overview

    @GetMapping("/settings")
    @PreAuthorize(VIEW)
    public SettingsDto settings() {
        return settingsService.get();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto saveSettings(@RequestBody SettingsRequest r) {
        return settingsService.update(r);
    }

    @GetMapping("/overview")
    @PreAuthorize(VIEW)
    public Overview overview() {
        return overviewService.overview();
    }

    // ---- owners

    @GetMapping("/owners")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<OwnerRow>> owners(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean activeOnly, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PmPaging.of(ownerService.list(q, activeOnly), page, size);
    }

    @GetMapping("/owners/{id}")
    @PreAuthorize(VIEW)
    public OwnerDto owner(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return ownerService.get(actor(me), id);
    }

    @PostMapping("/owners")
    @PreAuthorize(MANAGE)
    public OwnerDto createOwner(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody OwnerRequest r) {
        return ownerService.create(actor(me), r);
    }

    @PutMapping("/owners/{id}")
    @PreAuthorize(MANAGE)
    public OwnerDto updateOwner(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody OwnerRequest r) {
        return ownerService.update(actor(me), id, r);
    }

    @DeleteMapping("/owners/{id}")
    @PreAuthorize(MANAGE)
    public void deleteOwner(@PathVariable Long id) {
        ownerService.delete(id);
    }

    // ---- properties and units

    @GetMapping("/properties")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<PropertyRow>> properties(@RequestParam(required = false) String q, @RequestParam(required = false) Long ownerId, @RequestParam(required = false) Boolean activeOnly, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PmPaging.of(propertyService.list(q, ownerId, activeOnly), page, size);
    }

    @GetMapping("/properties/{id}")
    @PreAuthorize(VIEW)
    public PropertyDto property(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return propertyService.get(actor(me), id);
    }

    @PostMapping("/properties")
    @PreAuthorize(MANAGE)
    public PropertyDto createProperty(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody PropertyRequest r) {
        return propertyService.create(actor(me), r);
    }

    @PutMapping("/properties/{id}")
    @PreAuthorize(MANAGE)
    public PropertyDto updateProperty(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody PropertyRequest r) {
        return propertyService.update(actor(me), id, r);
    }

    @DeleteMapping("/properties/{id}")
    @PreAuthorize(MANAGE)
    public void deleteProperty(@PathVariable Long id) {
        propertyService.delete(id);
    }

    @PostMapping("/properties/{id}/units")
    @PreAuthorize(MANAGE)
    public UnitRow addUnit(@PathVariable Long id, @RequestBody UnitRequest r) {
        return propertyService.addUnit(id, r);
    }

    @PostMapping("/properties/{id}/units/bulk")
    @PreAuthorize(MANAGE)
    public List<UnitRow> addUnitsBulk(@PathVariable Long id, @RequestBody BulkUnitsRequest r) {
        return propertyService.addUnitsBulk(id, r);
    }

    @GetMapping("/units")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<UnitRow>> units(@RequestParam(required = false) String q, @RequestParam(required = false) Long propertyId, @RequestParam(required = false) String occupancy,
                               @RequestParam(required = false) Long ownerId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PmPaging.of(propertyService.units(q, propertyId, occupancy, ownerId), page, size);
    }

    @GetMapping("/units/{id}")
    @PreAuthorize(VIEW)
    public UnitDto unit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return propertyService.unit(actor(me), id);
    }

    @PutMapping("/units/{id}")
    @PreAuthorize(MANAGE)
    public UnitRow updateUnit(@PathVariable Long id, @RequestBody UnitRequest r) {
        return propertyService.updateUnit(id, r);
    }

    @DeleteMapping("/units/{id}")
    @PreAuthorize(MANAGE)
    public void deleteUnit(@PathVariable Long id) {
        propertyService.deleteUnit(id);
    }

    // ---- tenants

    @GetMapping("/tenants")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<TenantRow>> tenants(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean activeOnly, @RequestParam(required = false) Boolean withLeaseOnly, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PmPaging.of(tenantService.list(q, activeOnly, withLeaseOnly), page, size);
    }

    @GetMapping("/tenants/{id}")
    @PreAuthorize(VIEW)
    public TenantDto tenant(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return tenantService.get(actor(me), id);
    }

    @PostMapping("/tenants")
    @PreAuthorize(MANAGE)
    public TenantDto createTenant(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody TenantRequest r) {
        return tenantService.create(actor(me), r);
    }

    @PutMapping("/tenants/{id}")
    @PreAuthorize(MANAGE)
    public TenantDto updateTenant(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody TenantRequest r) {
        return tenantService.update(actor(me), id, r);
    }

    @DeleteMapping("/tenants/{id}")
    @PreAuthorize(MANAGE)
    public void deleteTenant(@PathVariable Long id) {
        tenantService.delete(id);
    }

    // ---- leases

    @GetMapping("/leases")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<LeaseRow>> leases(@RequestParam(required = false) String q, @RequestParam(required = false) LeaseStatus status, @RequestParam(required = false) String state,
                                 @RequestParam(required = false) Long unitId, @RequestParam(required = false) Long tenantId, @RequestParam(required = false) Long propertyId,
                                 @RequestParam(required = false) Long ownerId, @RequestParam(required = false) Boolean expiringOnly, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PmPaging.of(leaseService.list(q, status, state, unitId, tenantId, propertyId, ownerId, expiringOnly), page, size);
    }

    @GetMapping("/leases/{id}")
    @PreAuthorize(VIEW)
    public LeaseDto lease(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return leaseService.get(actor(me), id);
    }

    @PostMapping("/leases")
    @PreAuthorize(MANAGE)
    public LeaseDto createLease(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody LeaseRequest r) {
        return leaseService.create(actor(me), r);
    }

    @PutMapping("/leases/{id}")
    @PreAuthorize(MANAGE)
    public LeaseDto updateLease(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody LeaseRequest r) {
        return leaseService.update(actor(me), id, r);
    }

    @DeleteMapping("/leases/{id}")
    @PreAuthorize(MANAGE)
    public void deleteLease(@PathVariable Long id) {
        leaseService.delete(id);
    }

    @PostMapping("/leases/{id}/activate")
    @PreAuthorize(MANAGE)
    public LeaseDto activate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) ActivateRequest r) {
        return leaseService.activate(actor(me), id, r);
    }

    @PostMapping("/leases/{id}/terminate")
    @PreAuthorize(MANAGE)
    public LeaseDto terminate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody TerminateRequest r) {
        return leaseService.terminate(actor(me), id, r);
    }

    @PostMapping("/leases/{id}/end")
    @PreAuthorize(MANAGE)
    public LeaseDto end(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) EndRequest r) {
        return leaseService.end(actor(me), id, r);
    }

    @PostMapping("/leases/{id}/renew")
    @PreAuthorize(MANAGE)
    public LeaseDto renew(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) RenewRequest r) {
        return leaseService.renew(actor(me), id, r);
    }

    // ---- rent

    @GetMapping("/leases/{id}/rent")
    @PreAuthorize(VIEW)
    public RentSchedule leaseRent(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return rentService.schedule(actor(me), id);
    }

    @GetMapping("/rent")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<RentChargeRow>> rent(@RequestParam(required = false) String status, @RequestParam(required = false) Long leaseId, @RequestParam(required = false) Long tenantId,
                                    @RequestParam(required = false) Long propertyId, @RequestParam(required = false) Long ownerId,
                                    @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                                    @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PmPaging.of(rentService.list(status, leaseId, tenantId, propertyId, ownerId, from, to), page, size);
    }

    /** Invoices the rent that is ready (all leases, or one), up to {@code through}. */
    @PostMapping("/rent/invoice")
    @PreAuthorize(MANAGE)
    public InvoiceRun invoiceRent(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody(required = false) InvoiceRentRequest r) {
        return rentService.invoiceDue(actor(me), r == null ? null : r.leaseId(), r == null ? null : r.through());
    }

    @PostMapping("/rent/{id}/invoice")
    @PreAuthorize(MANAGE)
    public RentChargeRow invoiceOneRent(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return rentService.invoiceOne(actor(me), id);
    }

    @PostMapping("/rent/{id}/waive")
    @PreAuthorize(MANAGE)
    public RentChargeRow waiveRent(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody WaiveRequest r) {
        return rentService.waive(actor(me), id, r == null ? null : r.reason());
    }

    @PostMapping("/rent/{id}/restore")
    @PreAuthorize(MANAGE)
    public RentChargeRow restoreRent(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return rentService.restore(actor(me), id);
    }

    @GetMapping("/arrears")
    @PreAuthorize(VIEW)
    public Arrears arrears(@RequestParam(required = false) Long tenantId, @RequestParam(required = false) Long propertyId, @RequestParam(required = false) Long ownerId) {
        return rentService.arrears(tenantId, propertyId, ownerId);
    }

    // ---- maintenance

    @GetMapping("/maintenance")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<MaintenanceRow>> maintenance(@RequestParam(required = false) String q, @RequestParam(required = false) MaintenanceStatus status, @RequestParam(required = false) Boolean openOnly,
                                            @RequestParam(required = false) MaintenancePriority priority, @RequestParam(required = false) MaintenanceCategory category,
                                            @RequestParam(required = false) Long unitId, @RequestParam(required = false) Long propertyId, @RequestParam(required = false) Long ownerId,
                                            @RequestParam(required = false) Long tenantId, @RequestParam(required = false) Boolean overdueOnly, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PmPaging.of(maintenanceService.list(q, status, openOnly, priority, category, unitId, propertyId, ownerId, tenantId, overdueOnly), page, size);
    }

    @GetMapping("/maintenance/{id}")
    @PreAuthorize(VIEW)
    public MaintenanceDto maintenanceRequest(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return maintenanceService.get(actor(me), id);
    }

    @PostMapping("/maintenance")
    @PreAuthorize(MANAGE)
    public MaintenanceDto createMaintenance(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody MaintenanceRequest r) {
        return maintenanceService.create(actor(me), r);
    }

    @PutMapping("/maintenance/{id}")
    @PreAuthorize(MANAGE)
    public MaintenanceDto updateMaintenance(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody MaintenanceRequest r) {
        return maintenanceService.update(actor(me), id, r);
    }

    @DeleteMapping("/maintenance/{id}")
    @PreAuthorize(MANAGE)
    public void deleteMaintenance(@PathVariable Long id) {
        maintenanceService.delete(id);
    }

    @PostMapping("/maintenance/{id}/start")
    @PreAuthorize(MANAGE)
    public MaintenanceDto startMaintenance(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return maintenanceService.start(actor(me), id);
    }

    @PostMapping("/maintenance/{id}/hold")
    @PreAuthorize(MANAGE)
    public MaintenanceDto holdMaintenance(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return maintenanceService.hold(actor(me), id);
    }

    @PostMapping("/maintenance/{id}/complete")
    @PreAuthorize(MANAGE)
    public MaintenanceDto completeMaintenance(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) CompleteMaintenanceRequest r) {
        return maintenanceService.complete(actor(me), id, r);
    }

    @PostMapping("/maintenance/{id}/cancel")
    @PreAuthorize(MANAGE)
    public MaintenanceDto cancelMaintenance(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody CancelMaintenanceRequest r) {
        return maintenanceService.cancel(actor(me), id, r);
    }

    @PostMapping("/maintenance/{id}/reopen")
    @PreAuthorize(MANAGE)
    public MaintenanceDto reopenMaintenance(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return maintenanceService.reopen(actor(me), id);
    }

    @PostMapping("/maintenance/{id}/work-order")
    @PreAuthorize(MANAGE)
    public MaintenanceDto raiseWorkOrder(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return maintenanceService.raiseWorkOrder(actor(me), id);
    }

    @DeleteMapping("/maintenance/{id}/work-order")
    @PreAuthorize(MANAGE)
    public MaintenanceDto unlinkWorkOrder(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return maintenanceService.unlinkWorkOrder(actor(me), id);
    }

    @PostMapping("/maintenance/{id}/invoice-tenant")
    @PreAuthorize(MANAGE)
    public MaintenanceDto invoiceTenantForMaintenance(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return maintenanceService.invoiceTenant(actor(me), id);
    }
}
