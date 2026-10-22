package com.posapp.realestate;

import com.posapp.installment.dto.InstallmentDtos.PreviewDto;
import com.posapp.realestate.dto.RealEstateProjectDtos.*;
import com.posapp.realestate.dto.RealEstateProjectRequests.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** {@code /api/realestate}: developer projects, their units and price list, and the sales of units. Same access as the rest of Real Estate. */
@RestController
@RequestMapping("/api/realestate")
public class ReProjectController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final ReProjectService projectService;
    private final ReUnitSaleService saleService;

    public ReProjectController(ReProjectService projectService, ReUnitSaleService saleService) {
        this.projectService = projectService;
        this.saleService = saleService;
    }

    // ---- projects

    @GetMapping("/projects")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<ProjectRow>> projects(@RequestParam(required = false) String q, @RequestParam(required = false) ProjectStatus status, @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return RePaging.of(projectService.list(q, status), page, size);
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize(VIEW)
    public ProjectDto project(@PathVariable Long id) {
        return projectService.get(id);
    }

    @PostMapping("/projects")
    @PreAuthorize(MANAGE)
    public ProjectDto createProject(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody ProjectRequest r) {
        return projectService.create(RealEstateController.actor(me), r);
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize(MANAGE)
    public ProjectDto updateProject(@PathVariable Long id, @RequestBody ProjectRequest r) {
        return projectService.update(id, r);
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize(MANAGE)
    public void deleteProject(@PathVariable Long id) {
        projectService.delete(id);
    }

    @PostMapping("/projects/{id}/units")
    @PreAuthorize(MANAGE)
    public UnitDto createUnit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody UnitRequest r) {
        return projectService.createUnit(RealEstateController.actor(me), id, r);
    }

    @PostMapping("/projects/{id}/units/bulk")
    @PreAuthorize(MANAGE)
    public BulkResult bulkUnits(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody BulkUnitsRequest r) {
        return projectService.bulk(RealEstateController.actor(me), id, r);
    }

    @PostMapping("/projects/{id}/prices")
    @PreAuthorize(MANAGE)
    public PriceAdjustResult adjustPrices(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody PriceAdjustRequest r) {
        return projectService.adjustPrices(RealEstateController.actor(me), id, r);
    }

    // ---- units

    @GetMapping("/units")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<UnitRow>> units(@RequestParam(required = false) String q, @RequestParam(required = false) Long projectId, @RequestParam(required = false) UnitStatus status,
                               @RequestParam(required = false) UnitKind kind, @RequestParam(required = false) String block, @RequestParam(required = false) Boolean availableOnly,
                                                @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return RePaging.of(projectService.units(q, projectId, status, kind, block, availableOnly), page, size);
    }

    @GetMapping("/units/{id}")
    @PreAuthorize(VIEW)
    public UnitDto unit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return projectService.unit(RealEstateController.actor(me), id);
    }

    @PutMapping("/units/{id}")
    @PreAuthorize(MANAGE)
    public UnitDto updateUnit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody UnitRequest r) {
        return projectService.updateUnit(RealEstateController.actor(me), id, r);
    }

    @DeleteMapping("/units/{id}")
    @PreAuthorize(MANAGE)
    public void deleteUnit(@PathVariable Long id) {
        projectService.deleteUnit(id);
    }

    // ---- unit sales

    @GetMapping("/unit-sales")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<UnitSaleRow>> sales(@RequestParam(required = false) String q, @RequestParam(required = false) UnitSaleStage stage, @RequestParam(required = false) Long projectId,
                                   @RequestParam(required = false) Long unitId, @RequestParam(required = false) Boolean openOnly,
                                                      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return RePaging.of(saleService.list(q, stage, projectId, unitId, openOnly), page, size);
    }

    @GetMapping("/unit-sales/{id}")
    @PreAuthorize(VIEW)
    public UnitSaleDto sale(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return saleService.get(RealEstateController.actor(me), id);
    }

    @PostMapping("/unit-sales")
    @PreAuthorize(MANAGE)
    public UnitSaleDto reserve(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody UnitSaleRequest r) {
        return saleService.create(RealEstateController.actor(me), r);
    }

    @PutMapping("/unit-sales/{id}")
    @PreAuthorize(MANAGE)
    public UnitSaleDto updateSale(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody UnitSaleRequest r) {
        return saleService.update(RealEstateController.actor(me), id, r);
    }

    @PostMapping("/unit-sales/{id}/agreement")
    @PreAuthorize(MANAGE)
    public UnitSaleDto agreement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) SaleAgreementRequest r) {
        return saleService.agreement(RealEstateController.actor(me), id, r);
    }

    @PostMapping("/unit-sales/{id}/plan/preview")
    @PreAuthorize(MANAGE)
    public PreviewDto planPreview(@PathVariable Long id, @RequestBody UnitPlanRequest r) {
        return saleService.planPreview(id, r);
    }

    @PostMapping("/unit-sales/{id}/plan")
    @PreAuthorize(MANAGE)
    public UnitSaleDto createPlan(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody UnitPlanRequest r) {
        return saleService.createPlan(RealEstateController.actor(me), id, r);
    }

    @PostMapping("/unit-sales/{id}/complete")
    @PreAuthorize(MANAGE)
    public UnitSaleDto complete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) SaleCompleteRequest r) {
        return saleService.complete(RealEstateController.actor(me), id, r);
    }

    @PostMapping("/unit-sales/{id}/cancel")
    @PreAuthorize(MANAGE)
    public UnitSaleDto cancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody SaleCancelRequest r) {
        return saleService.cancel(RealEstateController.actor(me), id, r);
    }

    @DeleteMapping("/unit-sales/{id}")
    @PreAuthorize(MANAGE)
    public void deleteSale(@PathVariable Long id) {
        saleService.delete(id);
    }
}
