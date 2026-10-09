package com.posapp.subscription.dto;

import com.posapp.subscription.BillingInterval;
import com.posapp.subscription.SubscriptionPlan;
import com.posapp.tax.TaxRate;

import java.math.BigDecimal;

public record SubscriptionPlanDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        BillingInterval billingInterval,
        Long taxRateId,
        String taxRateCode,
        String taxRateName,
        BigDecimal taxRatePercent,
        boolean taxRateInclusive,
        BigDecimal discountPercent,
        boolean active
) {
    public static SubscriptionPlanDto from(SubscriptionPlan p) {
        TaxRate rate = p.getTaxRate();
        return new SubscriptionPlanDto(
                p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getBillingInterval(),
                rate != null ? rate.getId() : null,
                rate != null ? rate.getCode() : null,
                rate != null ? rate.getName() : null,
                rate != null ? rate.getRatePercent() : null,
                rate != null && rate.isInclusive(),
                p.getDiscountPercent(),
                p.isActive());
    }
}
