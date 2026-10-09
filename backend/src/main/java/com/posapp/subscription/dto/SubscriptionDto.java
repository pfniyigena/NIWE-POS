package com.posapp.subscription.dto;

import com.posapp.subscription.BillingInterval;
import com.posapp.subscription.Subscription;
import com.posapp.subscription.SubscriptionPlan;
import com.posapp.subscription.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SubscriptionDto(
        Long id,
        Long customerId,
        String customerName,
        Long planId,
        String planName,
        BigDecimal price,
        BillingInterval billingInterval,
        String taxCode,
        BigDecimal taxRatePercent,
        Boolean taxInclusive,
        BigDecimal discountPercent,
        /** Fourier: "may be some subscription will be paid after service other will be prepaid" — always a plain boolean on the wire (never null), true for a row predating this field. See Subscription.billInAdvance's own javadoc. */
        boolean billInAdvance,
        SubscriptionStatus status,
        LocalDate startDate,
        LocalDate nextBillingDate,
        Instant lastBilledAt,
        Instant cancelledAt,
        String cancelReason,
        String notes
) {
    public static SubscriptionDto from(Subscription s) {
        SubscriptionPlan plan = s.getPlan();
        return new SubscriptionDto(
                s.getId(), s.getCustomerId(), s.getCustomerName(),
                plan != null ? plan.getId() : null,
                plan != null ? plan.getName() : null,
                s.getPrice(), s.getBillingInterval(),
                s.getTaxCode(), s.getTaxRatePercent(), s.getTaxInclusive(), s.getDiscountPercent(),
                !Boolean.FALSE.equals(s.getBillInAdvance()),
                s.getStatus(),
                s.getStartDate(), s.getNextBillingDate(), s.getLastBilledAt(),
                s.getCancelledAt(), s.getCancelReason(), s.getNotes()
        );
    }
}
