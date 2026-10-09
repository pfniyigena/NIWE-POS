package com.posapp.salesorder.dto;

import com.posapp.common.TaxMath;
import com.posapp.salesorder.SalesOrderLine;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record SalesOrderLineDto(
        Long id,
        Long productId,
        String productName,
        int quantityOrdered,
        int quantityFulfilled,
        BigDecimal unitPrice,
        /**
         * Fourier: "Except POS other module can have global discount ... Either on line or
         * global" — the discount percentage actually applied to this line: either this line's
         * own entered value (PER_LINE mode) or the order's single header-level value applied
         * uniformly (GLOBAL mode), via TaxMath.effectiveDiscountPercent. Zero for a line with no
         * discount either way.
         */
        BigDecimal discountPercent,
        /** The currency amount knocked off by discountPercent — purely informational, already folded into lineTotal below. */
        BigDecimal discountAmount,
        BigDecimal lineTotal,
        /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — see SalesOrderLine.taxCode's javadoc. Null for a tax-exempt line. */
        String taxCode,
        BigDecimal taxRatePercent,
        BigDecimal taxTotal,
        boolean taxInclusive,
        /** NONE, BATCH or SERIAL — serial products ask for serial numbers when fulfilling. */
        String trackingMode
) {
    public static SalesOrderLineDto from(BigDecimal headerDiscountPercent, SalesOrderLine l) {
        BigDecimal grossBeforeDiscount = l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantityOrdered())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountPercent = TaxMath.effectiveDiscountPercent(headerDiscountPercent, l.getDiscountPercent());
        BigDecimal gross = TaxMath.applyDiscount(grossBeforeDiscount, discountPercent);
        BigDecimal discountAmount = grossBeforeDiscount.subtract(gross).setScale(2, RoundingMode.HALF_UP);
        boolean inclusive = Boolean.TRUE.equals(l.getTaxInclusive());
        BigDecimal tax = TaxMath.taxOnAmount(gross, l.getTaxRatePercent(), inclusive);
        return new SalesOrderLineDto(
                l.getId(),
                l.getProduct() != null ? l.getProduct().getId() : null,
                l.getProductName(),
                l.getQuantityOrdered(),
                l.getQuantityFulfilled(),
                l.getUnitPrice(),
                discountPercent,
                discountAmount,
                gross,
                l.getTaxCode(),
                l.getTaxRatePercent(),
                tax,
                inclusive,
                l.getProduct() != null && l.getProduct().getTrackingMode() != null ? l.getProduct().getTrackingMode().name() : "NONE"
        );
    }
}
