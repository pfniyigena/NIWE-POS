package com.posapp.subscription.dto;

import com.posapp.subscription.BillingInterval;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Body for POST/PUT /api/subscription-plans — see SubscriptionPlanService. */
public class SubscriptionPlanRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "Billing interval is required")
    private BillingInterval billingInterval = BillingInterval.MONTHLY;

    /** Fourier: "subscription plan should also have tax and discount on it" — null means this plan is tax-exempt, same as Product's own taxRateId. */
    private Long taxRateId;

    /** Fourier: "subscription plan should also have tax and discount on it" — this plan's own default, same 0-100 range every other discount field in this app validates. */
    @PositiveOrZero(message = "Discount cannot be negative")
    @DecimalMax(value = "100", message = "Discount cannot exceed 100%")
    private BigDecimal discountPercent = BigDecimal.ZERO;

    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BillingInterval getBillingInterval() { return billingInterval; }
    public void setBillingInterval(BillingInterval billingInterval) { this.billingInterval = billingInterval; }
    public Long getTaxRateId() { return taxRateId; }
    public void setTaxRateId(Long taxRateId) { this.taxRateId = taxRateId; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
