package com.posapp.tax;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A named, admin-manageable tax rate (e.g. "Standard VAT", code {@code STD}, 15%) — was a single
 * hardcoded {@code app.pos.tax-rate} property, then (eleventh pass) one admin-manageable rate
 * applied to every sale. As of this pass, tax is assigned **per product** (see
 * {@code Product.taxRate}) rather than one flat rate for the whole store, so — like
 * {@code PaymentMethodEntity} — any number of rates can be {@code active} at once; {@code active}
 * just means "available to assign to a product," not "the one currently in effect."
 *
 * <p>{@code code} is the stable identifier snapshotted onto {@code OrderLine} (as
 * {@code taxCode}/{@code taxRatePercent}) the moment a line is added to a cart, mirroring how
 * {@code Payment.method} stores a payment method's {@code code} rather than a foreign key:
 * renaming a rate's display name, changing its percentage, or even deactivating it, never
 * rewrites an already-rung sale line. {@code ratePercent} is a human-entered percentage (e.g.
 * {@code 15.00}), not a fraction.
 */
@Getter
@Setter
@Entity
public class TaxRate extends BaseEntity {

    /** Stable identifier snapshotted onto OrderLine rows — never shown to a cashier. */
    @Column(nullable = false, unique = true)
    private String code;

    /** Display label shown in the admin Taxes list and the product form's tax picker. */
    @Column(nullable = false)
    private String name;

    /** A percentage, e.g. 15.00 for 15% — not a fraction. */
    @Column(nullable = false)
    private BigDecimal ratePercent;

    /** Whether this rate can still be assigned to a product — not "the one in effect" (see class javadoc). */
    private boolean active = true;

    /**
     * When true, a product's {@code unitPrice} already has this rate's tax folded in (the
     * sticker/shelf price the customer sees is the final price) rather than tax being added on
     * top at checkout (Task: "if it is true the in the tax in price is inclusive"). Snapshotted
     * onto {@code OrderLine.taxInclusive} at add-line time, same as {@code code}/{@code
     * ratePercent} — see that field's javadoc. {@code OrderCalculator} is where this actually
     * changes the math: {@code lineTax} extracts the embedded tax instead of computing it on top,
     * and the order's subtotal/total are adjusted so nothing is double-counted.
     */
    private boolean inclusive = false;
}
