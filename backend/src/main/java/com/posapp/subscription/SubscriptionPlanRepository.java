package com.posapp.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    List<SubscriptionPlan> findAllByOrderByNameAsc();

    /** Active plans only — what the "New subscription" picker offers. */
    List<SubscriptionPlan> findByActiveTrueOrderByNameAsc();
}
