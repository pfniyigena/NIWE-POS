package com.posapp.subscription;

import com.posapp.common.BaseEntity;
import com.posapp.tax.TaxRate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Fourier: "Subscriptions Module" — scoped via AskUserQuestion to a fixed, admin-defined plan
 * (not tied to the existing Products catalog) that one or more customers can subscribe to (see
 * {@link Subscription}). {@link #price}/{@link #billingInterval} here are just this plan's own
 * current defaults — every {@link Subscription} snapshots both at the moment it's created (same
 * "denormalize at creation" convention as {@code SalesOrderLine.unitPrice}), so editing a plan's
 * price later never retroactively changes what an already-subscribed customer owes.
 *
 * <p>{@link #active} lets a plan be retired from new sign-ups without disturbing any existing
 * subscriber already on it — same "toggle, don't delete" convention as {@code
 * PaymentMethodEntity.active}/{@code TaxRate.active}. Since {@link Subscription} holds a real
 * {@code @ManyToOne} to this entity, deleting a plan outright is never offered at all — that
 * would orphan every subscription already on it.
 */
@Getter
@Setter
@Entity
public class SubscriptionPlan extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private BillingInterval billingInterval = BillingInterval.MONTHLY;

    /**
     * Fourier: "subscription plan should also have tax and discount on it" — same code-not-copy
     * relation as {@code Product.taxRate} (nullable: null means this plan is tax-exempt, same
     * "deliberately exempt" reasoning as that field's own javadoc). Just this plan's own current
     * default, same as {@link #price}/{@link #billingInterval} above — {@link Subscription}
     * snapshots the rate's {@code code}/{@code ratePercent}/{@code inclusive} at creation time
     * rather than re-reading this relation, so retagging or removing a plan's tax rate later never
     * rewrites an already-subscribed customer's own billed terms.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private TaxRate taxRate;

    /**
     * Fourier: "subscription plan should also have tax and discount on it" — this plan's own
     * default discount percentage, same "just a default, snapshotted and then freely editable per
     * subscription" treatment as {@link #price}/{@link #billingInterval}: the "New subscription"
     * form pre-fills from this value but the admin can override it per customer (see {@code
     * CreateSubscriptionRequest#discountPercent}'s own javadoc). Zero (never null) for a plan with
     * no discount, same convention as {@code InvoiceLine.discountPercent}.
     */
    @Column(nullable = false)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    private boolean active = true;
}
