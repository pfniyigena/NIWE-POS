package com.posapp.recurringinvoice;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line of a {@link RecurringInvoiceTemplate} — a catalog product plus the quantity/price this
 * customer is actually billed each cycle, same "product is required, no free-text lines"
 * convention {@code com.posapp.invoice.InvoiceLine}/{@code SalesOrderLine} already follow.
 *
 * <p>Unlike {@code InvoiceLine.unitPrice} (which falls back to the product's own current price
 * when the request leaves it blank), {@link #unitPrice} here is always required and never
 * re-derived from the live product at generation time — this is the agreed recurring price for
 * this customer, same "snapshot once, never re-read" treatment {@code
 * com.posapp.subscription.Subscription#getPrice()} gives a subscription's own price. A later
 * change to the product's catalog price never reaches back into an existing template; editing the
 * template itself is how the price actually charged going forward is changed.
 */
@Getter
@Setter
@Entity
public class RecurringInvoiceTemplateLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private RecurringInvoiceTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private int quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    /** Nullable — blank means every generated invoice line falls back to the product's own current description, same as a standalone invoice line left blank (see {@code InvoiceService.applyLines}). */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Per-line discount, only meaningful while the store's discount mode is PER_LINE — validated fresh against that mode at generation time, same as a standalone invoice line's own. Null/zero means no discount. */
    private BigDecimal discountPercent;
}
