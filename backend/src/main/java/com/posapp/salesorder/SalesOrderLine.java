package com.posapp.salesorder;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line of a {@link SalesOrder} — a product, how many were ordered, how many have actually
 * been fulfilled (shipped/handed over) so far, and what price it's being sold at. {@code
 * quantityFulfilled} is its own running total rather than a boolean flag, the same reasoning as
 * {@code com.posapp.inventory.PurchaseOrderLine.quantityReceived}: an order can be fulfilled in
 * more than one delivery, and {@link SalesOrderService#fulfillLines} needs to always know exactly
 * how much of this line is still outstanding.
 */
@Getter
@Setter
@Entity
public class SalesOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at the time this line was added, same "survives a later rename" reasoning as PurchaseOrderLine.productName. */
    private String productName;

    private int quantityOrdered;

    private int quantityFulfilled = 0;

    /** What this customer is being charged per unit for this order — independent of Product.price, since a specific order can be priced differently (same reasoning as PurchaseOrderLine.unitCost vs. Product.costPrice). */
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /**
     * Fourier: "Same for discount (but discount should be activate in the setting or
     * configuration) and it will be per line" — manually entered per line, validated and capped
     * by {@code StoreSettingsService.validateLineDiscount} (including whether {@code
     * StoreSettings.discountMode} is actually {@code PER_LINE}) before ever reaching this field.
     * Zero (never null) for a line with no discount, or when the order uses a GLOBAL discount
     * instead (see {@code SalesOrder.discountPercent}'s javadoc — resolved against this field at
     * read time via {@code TaxMath.effectiveDiscountPercent}). Carried through verbatim (via the
     * already-resolved effective value) onto the Invoice line generated from this one ({@code
     * InvoiceService.buildFromSalesOrder}), same "freeze what was actually ordered" reasoning as
     * taxCode/taxRatePercent just below.
     */
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /**
     * Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — snapshot
     * of the product's assigned tax rate at the moment this line was added, same code-not-
     * foreign-key convention as {@code com.posapp.order.OrderLine.taxCode}/{@code
     * taxRatePercent}. Both null if the product had no tax rate assigned (tax-exempt) at
     * add-line time, or there was no product at all. Carried through verbatim onto the Invoice
     * line generated from this one ({@code InvoiceService.buildFromSalesOrder}) rather than
     * re-derived from the live product, same "freeze what was actually ordered" reasoning as
     * productName/unitPrice.
     */
    private String taxCode;
    private BigDecimal taxRatePercent;

    /** Snapshot of the assigned rate's {@code inclusive} flag at add-line time — same reasoning as taxCode/taxRatePercent above. Null (not just false) for a tax-exempt line. */
    private Boolean taxInclusive;
}
