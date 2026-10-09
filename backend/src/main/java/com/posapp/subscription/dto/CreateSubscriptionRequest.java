package com.posapp.subscription.dto;

import com.posapp.subscription.BillingInterval;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Body for POST /api/subscriptions — see SubscriptionService.create. */
public class CreateSubscriptionRequest {

    @NotNull(message = "Customer is required")
    private Long customerId;

    @NotNull(message = "Plan is required")
    private Long planId;

    /**
     * Fourier: "What if the One Subscription can be assign to the customer with different price
     * and different Billing interval" — required, same as the plan's own fields, but the caller
     * (the "New subscription" form, pre-filled from the chosen plan and left editable) supplies
     * them explicitly rather than SubscriptionService silently copying {@code plan.getPrice()}.
     * This is what lets one customer get a negotiated price or a different cadence than the
     * plan's own listed defaults, while every other subscription on the same plan is unaffected.
     * Once saved, this is this subscription's own committed price/interval going forward — never
     * retroactively changed by a later edit to the plan itself, same "snapshot at creation"
     * convention as {@code SalesOrderLine.unitPrice}/{@code InvoiceLine.unitPrice}.
     */
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "Billing interval is required")
    private BillingInterval billingInterval;

    /**
     * Fourier: "subscription plan should also have tax and discount on it" — same "pre-filled
     * from the plan, left editable per customer" treatment as {@link #price}/{@link
     * #billingInterval} above. Null means this subscription is tax-exempt.
     */
    private Long taxRateId;

    /** Same pre-filled-but-editable treatment as {@link #taxRateId} above. Null is treated as zero (no discount). */
    @PositiveOrZero(message = "Discount cannot be negative")
    @DecimalMax(value = "100", message = "Discount cannot exceed 100%")
    private BigDecimal discountPercent;

    /** Defaults to today when left blank. */
    private LocalDate startDate;

    /**
     * Fourier: "may be some subscription will be paid after service other will be prepaid" —
     * chosen fresh for this one subscription, no plan-level default (see {@code
     * Subscription#billInAdvance}'s own javadoc for exactly what this changes). Null is treated as
     * {@code true} (prepaid), the same behavior every subscription had before this field existed.
     */
    private Boolean billInAdvance;

    private String notes;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BillingInterval getBillingInterval() { return billingInterval; }
    public void setBillingInterval(BillingInterval billingInterval) { this.billingInterval = billingInterval; }
    public Long getTaxRateId() { return taxRateId; }
    public void setTaxRateId(Long taxRateId) { this.taxRateId = taxRateId; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public Boolean getBillInAdvance() { return billInAdvance; }
    public void setBillInAdvance(Boolean billInAdvance) { this.billInAdvance = billInAdvance; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
