package com.posapp.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — until now,
 * {@code com.posapp.order.OrderLine} (the POS cart) was the only line type in this app that
 * snapshotted a product's assigned tax rate at all; Quotation, Sales Order, Invoice, Purchase
 * Order, and Bill lines carried none. This is the shared pure-math half of fixing that — the
 * exact same formula {@code com.posapp.order.OrderCalculator.taxOnAmount} already uses for POS,
 * pulled out here so those five document modules can reuse it without taking on a dependency on
 * the order/POS module (which stays free of any reverse dependency on them). Each module still
 * keeps its own small per-line/per-document calculator (mirroring {@code OrderCalculator}'s own
 * shape) built on top of this.
 *
 * <p>Fourier: "Same for discount (but discount should be activate in the setting or
 * configuration) and it will be per line" — {@link #applyDiscount} is the same POS-side formula
 * {@code OrderCalculator.lineTotal} already applies (discount knocked off a line's gross
 * <em>before</em> tax is even considered — see that method's own javadoc for why), pulled out
 * here for the same reason {@link #taxOnAmount} was: so Quotation/Sales Order/Invoice/Purchase
 * Order/Bill can each apply it to their own line's gross without a reverse dependency on the
 * order/POS module. Whether a line (or, per Fourier's later "Except POS other module can have
 * global discount ... Either on line or global", a whole document) may actually carry a nonzero
 * discount at all is gated by {@code StoreSettings.discountMode} (see {@code StoreSettingsService
 * .validateLineDiscount}/{@code validateGlobalDiscount}) — this class only does the arithmetic
 * once a value has already been accepted; see {@link #effectiveDiscountPercent} for how the two
 * modes resolve to a single percentage.
 */
public final class TaxMath {

    private static final int SCALE = 2;

    private TaxMath() {
    }

    /**
     * The tax on {@code amount} at {@code ratePercent}, either added on top (exclusive — the
     * default) or extracted back out of an amount that already includes it (inclusive: {@code
     * amount * rate/(1+rate)}, the standard tax-inclusive-price formula). Null-safe — a line with
     * no tax rate assigned (tax-exempt, or added before this feature existed) returns zero.
     */
    public static BigDecimal taxOnAmount(BigDecimal amount, BigDecimal ratePercent, boolean inclusive) {
        if (amount == null || ratePercent == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal fraction = ratePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        if (inclusive) {
            BigDecimal divisor = BigDecimal.ONE.add(fraction);
            return amount.multiply(fraction).divide(divisor, SCALE, RoundingMode.HALF_UP);
        }
        return amount.multiply(fraction).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * A line's pre-tax (net) contribution once {@code tax} (already computed via {@link
     * #taxOnAmount}) is set aside — for an exclusive-tax (or untaxed) line this is just {@code
     * grossAmount} unchanged; for an inclusive-tax line, {@code grossAmount} already has the tax
     * folded in, so this backs it back out.
     */
    public static BigDecimal netOfTax(BigDecimal grossAmount, BigDecimal tax, boolean inclusive) {
        if (grossAmount == null) {
            return BigDecimal.ZERO;
        }
        if (!inclusive) {
            return grossAmount.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return grossAmount.subtract(tax == null ? BigDecimal.ZERO : tax).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * {@code grossAmount} with {@code discountPercent} knocked off — same {@code gross * (1 -
     * discount/100)} formula {@code OrderCalculator.lineTotal} already applies for POS. Null-safe
     * both ways: a null/zero discount (the overwhelming common case — most lines never carry one)
     * returns {@code grossAmount} unchanged, and a null {@code grossAmount} returns zero.
     */
    public static BigDecimal applyDiscount(BigDecimal grossAmount, BigDecimal discountPercent) {
        if (grossAmount == null) {
            return BigDecimal.ZERO;
        }
        if (discountPercent == null || discountPercent.signum() == 0) {
            return grossAmount.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return grossAmount.multiply(factor).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Fourier: "Except POS other module can have global discount. But again give the user to
     * configure both discount. Either on line or global" — resolves which discount percentage
     * actually applies to one line: the document's own header {@code discountPercent} when it's
     * positive (GLOBAL mode — the exact same percentage applies uniformly to every line, so no
     * per-line proration is needed, unlike a flat currency amount would require), otherwise that
     * line's own {@code discountPercent} (PER_LINE mode, or no discount at all if both are zero).
     * The two are mutually exclusive by construction — {@code
     * StoreSettingsService.validateLineDiscount}/{@code validateGlobalDiscount} only ever let one
     * of them be populated for a given document, so there's no ambiguity about which one "wins"
     * here in practice; the header check simply comes first.
     */
    public static BigDecimal effectiveDiscountPercent(BigDecimal headerDiscountPercent, BigDecimal lineDiscountPercent) {
        if (headerDiscountPercent != null && headerDiscountPercent.signum() > 0) {
            return headerDiscountPercent;
        }
        return lineDiscountPercent == null ? BigDecimal.ZERO : lineDiscountPercent;
    }

    /**
     * Fourier: "Global discount should be a fixed amount or percentage with percentage as the
     * default" — converts a flat currency amount entered for a GLOBAL discount into the
     * equivalent percentage of {@code subtotalBeforeDiscount}, so every document type keeps
     * working with {@link #effectiveDiscountPercent}/{@link #applyDiscount} entirely unchanged —
     * a flat amount is just a percentage computed once up front, never re-prorated per line
     * (uniform either way). Null-safe: a null/non-positive amount, or a zero/negative subtotal
     * (nothing to divide into), returns zero rather than dividing by zero. The caller is expected
     * to run the result back through {@code StoreSettingsService.validateGlobalDiscount} for the
     * same store-wide cap every percentage-entered discount already respects.
     */
    public static BigDecimal percentFromFixedAmount(BigDecimal fixedAmount, BigDecimal subtotalBeforeDiscount) {
        if (fixedAmount == null || fixedAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (subtotalBeforeDiscount == null || subtotalBeforeDiscount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return fixedAmount.multiply(BigDecimal.valueOf(100)).divide(subtotalBeforeDiscount, 6, RoundingMode.HALF_UP);
    }
}
