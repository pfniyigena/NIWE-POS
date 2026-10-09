package com.posapp.bill;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line of a {@link Bill} — a snapshot of one {@code PurchaseOrderLine} at the moment the bill
 * was generated (quantity ordered, unit cost, and the computed line total), copied in once by
 * {@code BillService.buildFromPurchaseOrder} and never touched again — the accounts-payable
 * mirror of {@code com.posapp.invoice.InvoiceLine}. Referencing {@code Product} directly is fine
 * for the same reason it is on that line — {@code catalog} is a shared base module, it's only
 * {@code com.posapp.inventory}'s own order/payment entities this module stays decoupled from.
 */
@Getter
@Setter
@Entity
public class BillLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at generation time, same "survives a later rename" reasoning as every other line's productName field. */
    private String productName;

    private int quantity;

    private BigDecimal unitCost = BigDecimal.ZERO;

    /**
     * Fourier: "Same for discount (but discount should be activate in the setting or
     * configuration) and it will be per line" — copied straight from the PurchaseOrderLine this
     * was generated from ({@code BillService.buildFromPurchaseOrder}), same "freeze what was
     * actually ordered" reasoning as productName/unitCost. Zero (never null) for a line with no
     * discount. Already folded into {@code lineTotal} below — see that field's own javadoc.
     */
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /** Post-discount (see {@code discountPercent} above), pre-tax — same "discount applied before tax is even considered" ordering {@code com.posapp.order.OrderCalculator.lineTotal} already uses for POS. */
    private BigDecimal lineTotal = BigDecimal.ZERO;

    /**
     * Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — snapshot
     * of the product's assigned tax rate, copied straight from the PurchaseOrderLine this was
     * generated from ({@code BillService.buildFromPurchaseOrder}) rather than re-derived from the
     * live product — same "freeze what was actually ordered" reasoning as productName/unitCost.
     * Both null for a tax-exempt line or one with no product.
     */
    private String taxCode;
    private BigDecimal taxRatePercent;

    /** Snapshot of the assigned rate's {@code inclusive} flag — same reasoning as taxCode/taxRatePercent above. Null (not just false) for a tax-exempt line. */
    private Boolean taxInclusive;
}
