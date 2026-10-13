package com.posapp.packaging;

import com.posapp.packaging.dto.PackagingBreakdownDto;
import com.posapp.packaging.dto.PackagingBreakdownRequest;
import com.posapp.packaging.dto.PackagingRuleDto;
import com.posapp.packaging.dto.PackagingRuleRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin > Inventory > Packaging — ADMIN-only, same tier as Stock adjustments/Manufacturing. */
@RestController
@RequestMapping("/api/packaging")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
public class PackagingController {

    private final PackagingService packagingService;

    public PackagingController(PackagingService packagingService) {
        this.packagingService = packagingService;
    }

    /** `?activeOnly=true` narrows this to rules a new breakdown can actually use — see PackagingBreakdownComponent. */
    @GetMapping("/rules")
    public List<PackagingRuleDto> listRules(@RequestParam(required = false) Boolean activeOnly) {
        return Boolean.TRUE.equals(activeOnly) ? packagingService.listActiveRules() : packagingService.listRules();
    }

    @GetMapping("/rules/{id}")
    public PackagingRuleDto getRuleById(@PathVariable Long id) {
        return packagingService.getRuleById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/rules")
    public PackagingRuleDto createRule(@Valid @RequestBody PackagingRuleRequest request) {
        return packagingService.createRule(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PutMapping("/rules/{id}")
    public PackagingRuleDto updateRule(@PathVariable Long id, @Valid @RequestBody PackagingRuleRequest request) {
        return packagingService.updateRule(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/rules/{id}/active")
    public PackagingRuleDto setRuleActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return packagingService.setRuleActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    /** Optional `?ruleId=` narrows this to one rule's own history, same convention as RecurringInvoiceTemplateController.generatedInvoices. */
    @GetMapping("/breakdowns")
    public List<PackagingBreakdownDto> listBreakdowns(@RequestParam(required = false) Long ruleId) {
        return packagingService.listBreakdowns(ruleId);
    }

    /** Executes a breakdown right now — see PackagingService#breakdown's javadoc for why this is the only lifecycle step. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/breakdowns")
    public PackagingBreakdownDto runBreakdown(@Valid @RequestBody PackagingBreakdownRequest request) {
        return packagingService.breakdown(request);
    }
}
