package com.posapp.common;

/**
 * Fourier: "Global discount should be a fixed amount or percentage with percentage as the
 * default" — how the single whole-document discount was actually entered, on a Quotation/Sale
 * Order/Invoice/Purchase Order (and Bill/Credit Note/Refund, which inherit theirs) while {@code
 * StoreSettings.discountMode} is {@code GLOBAL}. Purely a display/edit-round-trip concern: either
 * way, the entered value is converted once, up front, into an equivalent {@code discountPercent}
 * (see {@code TaxMath.percentFromFixedAmount}) — every line/tax calculation downstream reads only
 * that resolved percentage and never needs to know which way it was entered. Meaningless (and
 * left at its default) while the document's discount mode isn't GLOBAL at all.
 */
public enum GlobalDiscountType {
    /** The admin typed a percentage directly — the default, and the only option before this feature existed. */
    PERCENT,
    /** The admin typed a flat currency amount instead; see {@code TaxMath.percentFromFixedAmount} for how it's turned into the percentage actually applied. */
    FIXED_AMOUNT;

    /**
     * {@code request.discountType} arrives as a plain, optional string (so the frontend never has
     * to know this enum's exact Java type, same convention {@code StoreSettingsService
     * .parseDiscountMode} already uses) — blank/null defaults to {@link #PERCENT}, since that's
     * what every document already did before this feature existed, and an unrecognized value is
     * rejected the same way every other invalid input in this app is, rather than the generic 400
     * a jakarta.validation annotation would produce for an enum-typed field.
     */
    public static GlobalDiscountType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PERCENT;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Discount type must be PERCENT or FIXED_AMOUNT");
        }
    }
}
