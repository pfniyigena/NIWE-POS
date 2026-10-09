package com.posapp.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByOrderByIdDesc();

    /** One customer's own subscriptions — backs the Subscriptions screen's own `?customerId=` filter and the customer's own view. */
    List<Subscription> findByCustomerIdOrderByIdDesc(Long customerId);

    /** The scheduled sweep's own input — every still-ACTIVE subscription whose next billing date has arrived (or passed, e.g. the app was offline that day). */
    List<Subscription> findByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus status, LocalDate date);
}
