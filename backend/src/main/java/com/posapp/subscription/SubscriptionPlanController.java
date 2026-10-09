package com.posapp.subscription;

import com.posapp.subscription.dto.SubscriptionPlanDto;
import com.posapp.subscription.dto.SubscriptionPlanRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin > Sales > Subscription plans — ADMIN-only, same tier as Invoices/Sale orders. */
@RestController
@RequestMapping("/api/subscription-plans")
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionPlanController {

    private final SubscriptionPlanService service;

    public SubscriptionPlanController(SubscriptionPlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<SubscriptionPlanDto> listAll() {
        return service.listAll();
    }

    /** Active plans only — what the "New subscription" picker offers. */
    @GetMapping("/active")
    public List<SubscriptionPlanDto> listActive() {
        return service.listActive();
    }

    @GetMapping("/{id}")
    public SubscriptionPlanDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public SubscriptionPlanDto create(@Valid @RequestBody SubscriptionPlanRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SubscriptionPlanDto update(@PathVariable Long id, @Valid @RequestBody SubscriptionPlanRequest request) {
        return service.update(id, request);
    }
}
