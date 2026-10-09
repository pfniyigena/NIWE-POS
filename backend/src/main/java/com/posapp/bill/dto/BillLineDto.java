package com.posapp.bill.dto;

import com.posapp.bill.BillLine;
import com.posapp.common.TaxMath;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record BillLineDto(
        Long id,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitCost,
        /** Fourier: "Same for discount ... it will be per line" — see BillLine.discountPercent's javadoc. Zero for a line with no discount. */
        BigDecimal discountPercent,
        /** The currency amount knocked off by discountPercent — purely informational, already folded into lineTotal below. Derived here (unitCost * quantity - lineTotal) since the pre-discount gross is never itself persisted. */
        BigDecimal discountAmount,
        BigDecimal lineTotal,
        /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — see BillLine.taxCode's javadoc. Null for a tax-exempt line. */
        String taxCode,
        BigDecimal taxRatePercent,
        BigDecimal taxTotal,
        boolean taxInclusive
) {
    public static BillLineDto from(BillLine l) {
        boolean inclusive = Boolean.TRUE.equals(l.getTaxInclusive());
        BigDecimal tax = TaxMath.taxOnAmount(l.getLineTotal(), l.getTaxRatePercent(), inclusive);
        BigDecimal discountPercent = l.getDiscountPercent() == null ? BigDecimal.ZERO : l.getDiscountPercent();
        BigDecimal grossBeforeDiscount = l.getUnitCost().multiply(BigDecimal.valueOf(l.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = grossBeforeDiscount.subtract(l.getLineTotal()).setScale(2, RoundingMode.HALF_UP);
        return new BillLineDto(
                l.getId(),
                l.getProduct() != null ? l.getProduct().getId() : null,
                l.getProductName(),
                l.getQuantity(),
                l.getUnitCost(),
                discountPercent,
                discountAmount,
                l.getLineTotal(),
                l.getTaxCode(),
                l.getTaxRatePercent(),
                tax,
                inclusive
        );
    }
}
