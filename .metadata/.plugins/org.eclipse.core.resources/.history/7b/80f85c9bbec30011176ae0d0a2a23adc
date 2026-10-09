package com.posapp.quotation;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line of a {@link Quotation} — a product, how many, and the price being offered. No
 * "fulfilled so far" counter the way {@code SalesOrderLine}/{@code PurchaseOrderLine} have one —
 * a quotation is never fulfilled, only accepted/rejected/converted as a whole (see {@link
 * QuotationStatus}'s javadoc).
 */
@Getter
@Setter
@Entity
public class QuotationLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at the time this line was added, same "survives a later rename" reasoning as SalesOrderLine.productName/PurchaseOrderLine.productName. */
    private String productName;

    private int quantity;

    /** The price being offered per unit — independent of Product.unitPrice, since a quote can offer a different price (same reasoning as SalesOrderLine.unitPrice vs. Product.unitPrice). */
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /**
     * Fourier: "Same for discount (but discount should be activate in the setting or
     * configuration) and it will be per line" — manually entered per line at the moment it's
     * added/edited (there's no product-level discount to derive this from, unlike taxCode just
     * below), validated and capped by {@code StoreSettingsService.validateLineDiscount} —
     * including whether {@code StoreSettings.discountMode} is actually {@code PER_LINE} — before
     * ever reaching this field. Zero (never null) for a line with no discount, or when the
     * quotation uses a GLOBAL discount instead (see {@code Quotation.discountPercent}'s javadoc
     * — resolved against this field at read time via {@code TaxMath.effectiveDiscountPercent}),
     * same default-not-null convention {@code com.posapp.order.OrderLine.discountPercent}
     * already uses.
     */
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /**
     * Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — snapshot
     * of the product's assigned tax rate at the moment this line was added, same code-not-
     * foreign-key convention as {@code com.posapp.order.OrderLine.taxCode}/{@code
     * taxRatePercent}: a later change to the product's tax assignment, a rename, or deactivating
     * it outright never rewrites an already-quoted line. Both null if the product had no tax
     * rate assigned (tax-exempt) at add-line time, or there was no product at all.
     */
    private String taxCode;
    private BigDecimal taxRatePercent;

    /**
     * Snapshot of the assigned rate's {@code inclusive} flag at add-line time — same reasoning as
     * taxCode/taxRatePercent above. Null (not just false) for a tax-exempt line; {@code
     * QuotationCalculator} treats null the same as false (exclusive/no tax).
     */
    private Boolean taxInclusive;
}
