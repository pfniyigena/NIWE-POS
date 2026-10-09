package com.posapp.settings;

/**
 * Fourier: "Except POS other module can have global discount. But again give the user to
 * configure both discount. Either on line or global" — the three ways Quotation/Sale Order/
 * Invoice/Purchase Order (and Bill/Credit Note/Refund, which inherit theirs) can apply a
 * discount, chosen once store-wide via {@link StoreSettings#getDiscountMode()}. Mutually
 * exclusive — a store picks exactly one at a time, never both PER_LINE and GLOBAL at once.
 * Doesn't touch the POS terminal's own discount at all (see {@code
 * com.posapp.order.OrderLine#getDiscountPercent()}'s own javadoc), which stays role-gated and
 * always available regardless of this setting.
 */
public enum DiscountMode {
    /** No discount concept at all on these document types — every line/document discountPercent field stays zero, and entering one is rejected. */
    NONE,
    /** A discount entered per line, same idea as the POS terminal's own — just without POS's role/permission gate. See e.g. {@code QuotationLine#getDiscountPercent()}. */
    PER_LINE,
    /** A single discount percentage entered once for the whole document, applied uniformly to every line. See e.g. {@code Quotation#getDiscountPercent()}. */
    GLOBAL
}
