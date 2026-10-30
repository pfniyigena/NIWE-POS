package com.posapp.subscription.dto;

import com.posapp.subscription.BillingInterval;
import com.posapp.subscription.Subscription;
import com.posapp.subscription.SubscriptionPlan;
import com.posapp.subscription.SubscriptionService;
import com.posapp.subscription.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A customer's subscription as the screens see it. The amounts are what is billed each cycle: quantity x price, less the discount. */
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
        boolean billInAdvance,
        SubscriptionStatus status,
        LocalDate startDate,
        LocalDate nextBillingDate,
        Instant lastBilledAt,
        Instant cancelledAt,
        String cancelReason,
        String notes,
        int quantity,
        BigDecimal setupFee,
        boolean setupFeeBilled,
        int trialDays,
        LocalDate trialEndDate,
        boolean onTrial,
        Integer termCycles,
        int cyclesBilled,
        boolean autoRenew,
        int renewals,
        LocalDate resumeOn,
        String pauseReason,
        Instant pausedAt,
        boolean cancelAtPeriodEnd,
        BigDecimal creditBalance,
        Instant endedAt,
        int dunningCount,
        LocalDate dunningLastOn,
        /** quantity x price less discount, before tax, per billing cycle. */
        BigDecimal cycleAmount,
        /** The same amount normalised to a month (weekly x 52/12, quarterly / 3, yearly / 12). */
        BigDecimal monthlyValue
) {
    public static SubscriptionDto from(Subscription s) {
        SubscriptionPlan plan = s.getPlan();
        LocalDate today = LocalDate.now();
        BigDecimal cycle = SubscriptionService.cycleNet(s);
        return new SubscriptionDto(
                s.getId(), s.getCustomerId(), s.getCustomerName(),
                plan != null ? plan.getId() : null,
                plan != null ? plan.getName() : null,
                s.getPrice(), s.getBillingInterval(),
                s.getTaxCode(), s.getTaxRatePercent(), s.getTaxInclusive(), s.getDiscountPercent(),
                !Boolean.FALSE.equals(s.getBillInAdvance()),
                s.getStatus(),
                s.getStartDate(), s.getNextBillingDate(), s.getLastBilledAt(),
                s.getCancelledAt(), s.getCancelReason(), s.getNotes(),
                s.getQuantity(), s.getSetupFee(), s.isSetupFeeBilled(), s.getTrialDays(), s.getTrialEndDate(),
                s.getTrialEndDate() != null && s.getTrialEndDate().isAfter(today) && s.getStatus() == SubscriptionStatus.ACTIVE,
                s.getTermCycles(), s.getCyclesBilled(), s.isAutoRenew(), s.getRenewals(),
                s.getResumeOn(), s.getPauseReason(), s.getPausedAt(), s.isCancelAtPeriodEnd(), s.getCreditBalance(), s.getEndedAt(),
                s.getDunningCount(), s.getDunningLastOn(),
                cycle, SubscriptionService.monthly(cycle, s.getBillingInterval())
        );
    }
}
