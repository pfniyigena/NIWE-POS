package com.posapp.invoice;

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
 * One line of an {@link Invoice} — a snapshot of one {@code SalesOrderLine} at the moment the
 * invoice was generated (quantity ordered, unit price, and the computed line total), copied in
 * once by {@code InvoiceService.generate} and never touched again. Referencing {@code Product}
 * directly (unlike {@code Invoice.salesOrderId}) is fine — {@code catalog} is a shared base
 * module every line-item entity in this app already depends on directly (see {@code
 * PurchaseOrderLine}/{@code SalesOrderLine}), it's specifically the Sales module's own entities
 * this module stays decoupled from.
 */
@Getter
@Setter
@Entity
public class InvoiceLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at generation time, same "survives a later rename" reasoning as every other line's productName field. */
    private String productName;

    /**
     * Fourier: "On Invoice Line add product description text (by default it should pick the
     * product description)" — same snapshot-at-add-line-time convention as {@code productName}
     * above: defaults to {@code Product.description} the moment this line is added ({@code
     * InvoiceService.applyLines}/{@code generate}), but editable per line afterward and never
     * rewritten by a later edit to the product's own description. Nullable — most lines leave it
     * blank, same as most products never set one.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    private int quantity;

    private BigDecimal unitPrice = BigDecimal.ZERO;

    /**
     * Fourier: "Same for discount (but discount should be activate in the setting or
     * configuration) and it will be per line" — manually entered (standalone line) or carried
     * through verbatim from the SalesOrderLine this was generated from (same "freeze what was
     * actually ordered" reasoning as taxCode/taxRatePercent below), validated and capped by
     * {@code StoreSettingsService.validateLineDiscount} before ever reaching a standalone line.
     * Zero (never null) for a line with no discount. Already folded into {@code lineTotal} below
     * — see that field's own javadoc.
     */
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /** Post-discount (see {@code discountPercent} above), pre-tax — same "discount applied before tax is even considered" ordering {@code com.posapp.order.OrderCalculator.lineTotal} already uses for POS. */
    private BigDecimal lineTotal = BigDecimal.ZERO;

    /**
     * Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — snapshot
     * of the product's assigned tax rate, same code-not-foreign-key convention as {@code
     * com.posapp.order.OrderLine.taxCode}/{@code taxRatePercent}. For a Sales-Order-derived line
     * this is copied straight from the SalesOrderLine it was generated from ({@code
     * InvoiceService.buildFromSalesOrder}) rather than re-derived from the live product — same
     * "freeze what the order actually said" reasoning as productName/unitPrice; for a standalone
     * line it's looked up fresh from the product at add-line time. Both null for a tax-exempt
     * line, a line with no product (e.g. a subscription line), or one predating this field.
     */
    private String taxCode;
    private BigDecimal taxRatePercent;

    /** Snapshot of the assigned rate's {@code inclusive} flag — same reasoning as taxCode/taxRatePercent above. Null (not just false) for a tax-exempt line. */
    private Boolean taxInclusive;
}
