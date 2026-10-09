package com.posapp.quotation.dto;

import com.posapp.common.TaxMath;
import com.posapp.quotation.QuotationLine;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record QuotationLineDto(
        Long id,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        /**
         * Fourier: "Except POS other module can have global discount ... Either on line or
         * global" — the discount percentage actually applied to this line: either this line's
         * own entered value (PER_LINE mode — see QuotationLine.discountPercent's javadoc) or the
         * document's single header-level value applied uniformly (GLOBAL mode — see
         * Quotation.discountPercent's javadoc), via TaxMath.effectiveDiscountPercent. Zero for a
         * line with no discount either way.
         */
        BigDecimal discountPercent,
        /** The currency amount knocked off by discountPercent — purely informational, already folded into lineTotal below. */
        BigDecimal discountAmount,
        BigDecimal lineTotal,
        /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — see QuotationLine.taxCode's javadoc. Null for a tax-exempt line. */
        String taxCode,
        BigDecimal taxRatePercent,
        BigDecimal taxTotal,
        boolean taxInclusive
) {
    public static QuotationLineDto from(BigDecimal headerDiscountPercent, QuotationLine l) {
        BigDecimal grossBeforeDiscount = l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountPercent = TaxMath.effectiveDiscountPercent(headerDiscountPercent, l.getDiscountPercent());
        BigDecimal gross = TaxMath.applyDiscount(grossBeforeDiscount, discountPercent);
        BigDecimal discountAmount = grossBeforeDiscount.subtract(gross).setScale(2, RoundingMode.HALF_UP);
        boolean inclusive = Boolean.TRUE.equals(l.getTaxInclusive());
        BigDecimal tax = TaxMath.taxOnAmount(gross, l.getTaxRatePercent(), inclusive);
        return new QuotationLineDto(
                l.getId(),
                l.getProduct() != null ? l.getProduct().getId() : null,
                l.getProductName(),
                l.getQuantity(),
                l.getUnitPrice(),
                discountPercent,
                discountAmount,
                gross,
                l.getTaxCode(),
                l.getTaxRatePercent(),
                tax,
                inclusive
        );
    }
}
