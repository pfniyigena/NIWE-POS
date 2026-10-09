package com.posapp.quotation;

import com.posapp.common.BaseEntity;
import com.posapp.common.GlobalDiscountType;
import com.posapp.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A non-binding price offer given to a customer before they've committed to buying anything
 * (Fourier: "Build Quotation, Invoice, and the purchase-requisition approval gate ... without
 * touching warehousing" — see the earlier conversation on how these three differ). Deliberately
 * separate from {@code com.posapp.salesorder.SalesOrder}: nothing here reserves stock, checks a
 * customer's credit limit, or creates anything owed — see {@link QuotationStatus}'s javadoc for
 * the lifecycle. Once a customer ACCEPTS one, {@link QuotationService#convertToSalesOrder} copies
 * its lines into a brand-new {@code SalesOrder} (a real commitment) via {@code
 * SalesOrderService}'s own public {@code createDraft} — this class deliberately never touches
 * that module's entities directly, only its public service method, the same "cross-module calls
 * go through a service's public methods" convention the rest of this codebase follows. Once
 * converted, {@link #convertedSalesOrderId}/{@link #convertedSalesOrderNumber} record which Sales
 * Order this quote became, kept here (not as a field added to {@code SalesOrder} itself) so this
 * feature never has to modify the Sales module's own schema.
 * <p>
 * {@code quoteNumber} is this quotation's own human-readable identifier, same {@code "XX-" +
 * millis} convention as {@code SalesOrder.orderNumber}/{@code PurchaseOrder.orderNumber} — its own
 * {@code "QT-"} prefix keeps it from ever colliding with those.
 */
@Getter
@Setter
@Entity
public class Quotation extends BaseEntity {

    private String quoteNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private QuotationStatus status = QuotationStatus.DRAFT;

    private Instant quoteDate = Instant.now();

    /** Nullable — an optional "this price offer is only good until" date, purely informational (nothing here auto-expires it; see QuotationService.expire). */
    private LocalDate validUntil;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Fourier: "Except POS other module can have global discount. But again give the user to
     * configure both discount. Either on line or global" — a single discount percentage applied
     * uniformly to every line on this quotation, populated only when {@code
     * StoreSettings.discountMode} was GLOBAL at the moment this quote was created/edited (see
     * {@code QuotationLine.discountPercent}'s own javadoc for the PER_LINE case instead). Zero
     * (never null) for a quotation with no discount at all, or one entered per line instead —
     * see {@code StoreSettingsService.validateGlobalDiscount}, which enforces that only one of
     * the two is ever populated for a given document.
     */
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /**
     * Fourier: "Global discount should be a fixed amount or percentage with percentage as the
     * default" — how {@link #discountPercent} above was actually entered while in GLOBAL mode:
     * typed directly as a percentage ({@code PERCENT}, the default), or converted from a flat
     * amount ({@code FIXED_AMOUNT} — see {@link #discountFixedAmount}). Purely a display/edit
     * round-trip concern; every line/tax calculation downstream still reads only the resolved
     * {@link #discountPercent}. Meaningless (and left at its default) outside GLOBAL mode.
     */
    @Enumerated(EnumType.STRING)
    private GlobalDiscountType discountType = GlobalDiscountType.PERCENT;

    /**
     * Fourier: "Global discount should be a fixed amount or percentage" — the flat currency
     * amount the admin actually typed when {@link #discountType} is {@code FIXED_AMOUNT}; null
     * otherwise. {@code QuotationService} converts this once, at save time, into the equivalent
     * {@link #discountPercent} (see {@code TaxMath.percentFromFixedAmount}) — this field is kept
     * only so re-opening the draft shows back the amount that was typed, not a converted
     * percentage.
     */
    private BigDecimal discountFixedAmount;

    /** Set once this quote is CONVERTED — see class javadoc for why this lives here rather than as a field on SalesOrder. */
    private Long convertedSalesOrderId;

    private String convertedSalesOrderNumber;

    /**
     * Dedup flag for {@code QuotationService}'s daily expiry-warning sweep (Fourier: "Wiring a
     * handful of business events to that same mailer") — set true the moment the "expiring soon"
     * alert is sent for this quote, so a SENT quotation sitting in the warning window for several
     * days in a row (nothing here moves it out of SENT on its own) only ever alerts once rather
     * than once per day until someone acts on it or it's marked {@code EXPIRED}.
     */
    private boolean expiryAlertSent = false;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuotationLine> lines = new ArrayList<>();

    /**
     * Fourier: "How business branches can be introduced?" — the branch this quotation is issued from; chosen on the quotation, head office by default.
     * Null = head office, which is what a row created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;
}
