package com.posapp.law;

import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawBillingDtos.*;
import com.posapp.law.dto.LawBillingRequests.*;
import com.posapp.security.AppUserPrincipal;
import com.posapp.timetracking.dto.TimeRequests.RateRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fee terms, rates, stages, costs and billing of a matter. Reading and the matter-level changes follow the matter's own rules (see {@link LawAccess});
 * turning work into an invoice, asking for a retainer and taking a bill back also need Billing (manage).
 */
@RestController
@RequestMapping("/api/law")
@PreAuthorize("isAuthenticated()")
public class LawBillingController {

    private static final String BILL = "hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE')";
    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final LawBillingService service;

    public LawBillingController(LawBillingService service) {
        this.service = service;
    }

    private static Set<String> auth(AppUserPrincipal me) {
        return me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = auth(me);
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), admin, viewer, manager);
    }

    private static boolean canBill(AppUserPrincipal me) {
        Set<String> auth = auth(me);
        return auth.contains("ROLE_ADMIN") || auth.contains("AREA_BILLING_MANAGE");
    }

    @GetMapping("/matters/{id}/billing")
    public BillingDto billing(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.billing(actor(me), id, canBill(me));
    }

    @PutMapping("/matters/{id}/fee-terms")
    public BillingDto terms(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody FeeTermsRequest request) {
        return service.updateTerms(actor(me), id, request, canBill(me));
    }

    @PutMapping("/matters/{id}/rates")
    public BillingDto setRate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody RateRequest request) {
        return service.setRate(actor(me), id, request.userId(), request.hourlyRate(), canBill(me));
    }

    @DeleteMapping("/matters/{id}/rates/{userId}")
    public BillingDto removeRate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long userId) {
        return service.removeRate(actor(me), id, userId, canBill(me));
    }

    @PostMapping("/matters/{id}/time/approve")
    public BillingDto approveTime(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody DecisionRequest request) {
        return service.approveTime(actor(me), id, request, canBill(me));
    }

    @PostMapping("/matters/{id}/time/reject")
    public BillingDto rejectTime(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody DecisionRequest request) {
        return service.rejectTime(actor(me), id, request, canBill(me));
    }

    // ---- stages

    @PostMapping("/matters/{id}/stages")
    public BillingDto addStage(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody StageRequest request) {
        return service.addStage(actor(me), id, request, canBill(me));
    }

    @PutMapping("/matters/{id}/stages/{stageId}")
    public BillingDto updateStage(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long stageId, @RequestBody StageRequest request) {
        return service.updateStage(actor(me), id, stageId, request, canBill(me));
    }

    @DeleteMapping("/matters/{id}/stages/{stageId}")
    public BillingDto deleteStage(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long stageId) {
        return service.deleteStage(actor(me), id, stageId, canBill(me));
    }

    // ---- costs

    @PostMapping("/matters/{id}/costs")
    public BillingDto addCost(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody CostRequest request) {
        return service.addCost(actor(me), id, request, canBill(me));
    }

    @PutMapping("/matters/{id}/costs/{costId}")
    public BillingDto updateCost(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long costId, @RequestBody CostRequest request) {
        return service.updateCost(actor(me), id, costId, request, canBill(me));
    }

    @DeleteMapping("/matters/{id}/costs/{costId}")
    public BillingDto deleteCost(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long costId) {
        return service.deleteCost(actor(me), id, costId, canBill(me));
    }

    // ---- billing

    @PreAuthorize(BILL)
    @PostMapping("/matters/{id}/bill-preview")
    public PreviewDto preview(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) BillRequest request) {
        return service.preview(actor(me), id, request);
    }

    @PreAuthorize(BILL)
    @PostMapping("/matters/{id}/bill")
    public BillResult bill(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) BillRequest request) {
        return service.createBill(actor(me), id, request);
    }

    @PreAuthorize(BILL)
    @PostMapping("/matters/{id}/retainer-request")
    public BillResult retainer(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) RetainerRequest request) {
        return service.requestRetainer(actor(me), id, request);
    }

    @PreAuthorize(BILL)
    @PostMapping("/matters/{id}/bills/{billId}/release")
    public BillingDto release(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long billId) {
        return service.release(actor(me), id, billId, canBill(me));
    }

    // ---- across matters

    @PreAuthorize(VIEW)
    @GetMapping("/billing/overview")
    public List<OverviewRow> overview(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "true") boolean openOnly) {
        return service.overview(actor(me), openOnly);
    }
}
