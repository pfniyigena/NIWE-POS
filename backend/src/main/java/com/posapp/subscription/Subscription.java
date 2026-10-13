package com.posapp.subscription;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Fourier: "Subscriptions Module" — one customer's own enrollment in a {@link SubscriptionPlan}.
 * Holds {@link #customerId}/{@link #customerName} as plain denormalized fields rather than a
 * {@code @ManyToOne Customer}, the same convention {@code com.posapp.invoice.Invoice} already
 * uses for its own customer link — this module's whole output is an {@code Invoice}, generated
 * only through {@code InvoiceService}'s own public API ({@code
 * InvoiceService#generateForSubscription} — see {@link SubscriptionService#billOne}), so it
 * stays as decoupled from the Customer entity's own internals as Invoice already is.
 *
 * <p>{@link #price}/{@link #billingInterval} are snapshotted from the plan at creation time (see
 * {@link SubscriptionPlan}'s own javadoc) — {@link #plan} itself is still kept as a real relation
 * (unlike the customer link above) purely so the admin screens can show/filter by which plan a
 * subscription came from; nothing here ever re-reads the plan's own current price once a
 * subscription exists.
 *
 * <p>{@link #nextBillingDate} starts at {@link #startDate} (prepaid — see {@link #billInAdvance})
 * or one {@link #billingInterval} after it (paid after service), and advances by {@link
 * #billingInterval} every time {@link SubscriptionService}'s daily sweep (or its manual "bill
 * now") actually bills this subscription — never set directly by anyone once it exists, same
 * "advances only through being run" convention as {@code RecurringExpenseTemplate.nextRunDate}.
 */
@Getter
@Setter
@Entity
public class Subscription extends BaseEntity {

    private Long customerId;

    private String customerName;

    @ManyToOne(fetch = FetchType.LAZY)
    private SubscriptionPlan plan;

    /** Snapshotted from the plan at creation time — see this class's own javadoc. */
    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private BillingInterval billingInterval;

    /**
     * Fourier: "subscription plan should also have tax and discount on it" — snapshotted once at
     * creation time from the request (which pre-fills from {@link SubscriptionPlan#getTaxRate()}
     * but stays editable per customer), same code-not-foreign-key convention as {@code
     * InvoiceLine.taxCode}/{@code taxRatePercent}/{@code taxInclusive} and the same "freeze at
     * creation, never re-read" treatment {@link #price}/{@link #billingInterval} already get —
     * {@link SubscriptionService#billOne} copies these straight onto each generated invoice line.
     * Both null for a tax-exempt subscription.
     */
    private String taxCode;
    private BigDecimal taxRatePercent;

    /** Snapshot of the assigned rate's {@code inclusive} flag — same reasoning as taxCode/taxRatePercent above. Null (not just false) for a tax-exempt subscription. */
    private Boolean taxInclusive;

    /**
     * Fourier: "subscription plan should also have tax and discount on it" — snapshotted once at
     * creation time from the request (pre-filled from {@link SubscriptionPlan#getDiscountPercent()}
     * but editable per customer), same treatment as {@link #price}/{@link #billingInterval} and
     * {@code InvoiceLine.discountPercent}. Zero (never null) for no discount.
     */
    @Column(nullable = false)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /**
     * Fourier: "may be some subscription will be paid after service other will be prepaid" —
     * chosen fresh for each individual subscription at creation time (no plan-level default; see
     * {@code CreateSubscriptionRequest#billInAdvance}), and purely about when the *first* invoice
     * falls due: {@code true} (prepaid, the default — same behavior as before this field existed)
     * means {@link #startDate} itself is the first billing date, so the customer is billed before
     * that cycle's service is rendered; {@code false} (paid after service) means the first billing
     * date is {@link #startDate} advanced by one {@link #billingInterval}, so the customer gets a
     * full cycle of service before the first invoice. Never re-read after creation — only {@link
     * SubscriptionService#create} ever uses it, to compute the initial {@link #nextBillingDate};
     * every later cycle just advances by {@link #billingInterval} regardless of this flag.
     * Nullable (not a primitive) so a row from before this field existed loads as {@code null}
     * rather than failing a NOT NULL constraint on an existing table; {@code null} is treated as
     * {@code true} (prepaid) everywhere it's read, same "defaults to today's unchanged behavior"
     * reasoning as every other backward-compatible nullable flag in this app, e.g. {@code
     * TaxRate.inclusive} before it got a real default.
     */
    private Boolean billInAdvance = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate nextBillingDate;

    /** Set only by SubscriptionService's scheduled sweep or its manual "bill now" — null means this subscription has never actually been billed yet. */
    private Instant lastBilledAt;

    private Instant cancelledAt;

    @Column(columnDefinition = "TEXT")
    private String cancelReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** The nextBillingDate a billing reminder email was last sent for, so each upcoming bill is announced only once. */
    @Column(name = "reminder_sent_for")
    private LocalDate reminderSentFor;
}
