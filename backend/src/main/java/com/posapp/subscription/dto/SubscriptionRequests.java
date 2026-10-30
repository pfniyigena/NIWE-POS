package com.posapp.subscription.dto;

import com.posapp.subscription.BillingInterval;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Requests of the v1.19.0 subscription actions. */
public final class SubscriptionRequests {
    private SubscriptionRequests() {
    }

    /** Edits the terms; they apply from the next bill and nothing is charged or credited now. A null taxRateId means tax-exempt, a null termCycles means open-ended. */
    public record UpdateRequest(BigDecimal price, BillingInterval billingInterval, Integer quantity, BigDecimal discountPercent, Long taxRateId,
                                BigDecimal setupFee, Integer termCycles, Boolean autoRenew, String notes) {
    }

    /** Moves the customer to another plan and/or terms. With prorate, the unused part of a prepaid cycle already billed is settled now. */
    public record ChangePlanRequest(Long planId, BigDecimal price, BillingInterval billingInterval, Integer quantity, BigDecimal discountPercent,
                                    Long taxRateId, Boolean prorate) {
    }

    public record PauseRequest(String reason, LocalDate resumeOn) {
    }

    public record CancelRequest(String reason, Boolean atPeriodEnd) {
    }

    public record SettingsRequest(Boolean dunningEnabled, Integer dunningFirstDays, Integer dunningRepeatDays, Integer dunningMax, Integer catchUpMax) {
    }
}
