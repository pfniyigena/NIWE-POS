package com.posapp.subscription;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.subscription.dto.SubscriptionPlanDto;
import com.posapp.subscription.dto.SubscriptionPlanRequest;
import com.posapp.tax.TaxRateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Admin > Sales > Subscription plans — CRUD for the catalog {@link SubscriptionPlan}s a customer can subscribe to (see {@link SubscriptionService}). */
@Service
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository repository;
    private final AuditLogService auditLogService;
    private final TaxRateService taxRateService;

    public SubscriptionPlanService(SubscriptionPlanRepository repository, AuditLogService auditLogService, TaxRateService taxRateService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.taxRateService = taxRateService;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream().map(SubscriptionPlanDto::from).toList();
    }

    /** Active plans only — what the "New subscription" picker offers. */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(SubscriptionPlanDto::from).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanDto getById(Long id) {
        return SubscriptionPlanDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public SubscriptionPlanDto create(SubscriptionPlanRequest request) {
        SubscriptionPlan plan = new SubscriptionPlan();
        applyRequest(plan, request);
        SubscriptionPlan saved = repository.save(plan);
        auditLogService.record("SUBSCRIPTION_PLAN_CREATED",
                "Created subscription plan '" + saved.getName() + "' (" + saved.getPrice() + " / " + saved.getBillingInterval() + ")",
                "SubscriptionPlan", saved.getId());
        return SubscriptionPlanDto.from(saved);
    }

    /** Freely editable at any time; an existing subscriber's own price/interval is unaffected — see SubscriptionPlan's own javadoc. */
    @Transactional
    public SubscriptionPlanDto update(Long id, SubscriptionPlanRequest request) {
        SubscriptionPlan plan = getEntityOrThrow(id);
        applyRequest(plan, request);
        SubscriptionPlan saved = repository.save(plan);
        auditLogService.record("SUBSCRIPTION_PLAN_UPDATED", "Updated subscription plan '" + saved.getName() + "'", "SubscriptionPlan", saved.getId());
        return SubscriptionPlanDto.from(saved);
    }

    private void applyRequest(SubscriptionPlan plan, SubscriptionPlanRequest request) {
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setBillingInterval(request.getBillingInterval());
        // Fourier: "subscription plan should also have tax and discount on it" — same null-safe
        // resolution CatalogService already uses for Product.taxRateId.
        if (request.getTaxRateId() != null) {
            plan.setTaxRate(taxRateService.getEntityOrThrow(request.getTaxRateId()));
        } else {
            plan.setTaxRate(null);
        }
        plan.setDiscountPercent(request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO);
        plan.setActive(request.isActive());
    }

    private SubscriptionPlan getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subscription plan not found: " + id));
    }
}
