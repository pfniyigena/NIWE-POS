package com.posapp.invoice.dto;

import com.posapp.common.TaxMath;
import com.posapp.invoice.InvoiceLine;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record InvoiceLineDto(
        Long id,
        Long productId,
        String productName,
        /** See InvoiceLine.description's javadoc. */
        String description,
        int quantity,
        BigDecimal unitPrice,
        /** Fourier: "Same for discount ... it will be per line" — see InvoiceLine.discountPercent's javadoc. Zero for a line with no discount. */
        BigDecimal discountPercent,
        /** The currency amount knocked off by discountPercent — purely informational, already folded into lineTotal below. Derived here (unitPrice * quantity - lineTotal) since, unlike QuotationLine/SalesOrderLine, the pre-discount gross is never itself persisted. */
        BigDecimal discountAmount,
        BigDecimal lineTotal,
        /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — see InvoiceLine.taxCode's javadoc. Null for a tax-exempt line. */
        String taxCode,
        BigDecimal taxRatePercent,
        BigDecimal taxTotal,
        boolean taxInclusive
) {
    public static InvoiceLineDto from(InvoiceLine l) {
        boolean inclusive = Boolean.TRUE.equals(l.getTaxInclusive());
        BigDecimal tax = TaxMath.taxOnAmount(l.getLineTotal(), l.getTaxRatePercent(), inclusive);
        BigDecimal discountPercent = l.getDiscountPercent() == null ? BigDecimal.ZERO : l.getDiscountPercent();
        BigDecimal grossBeforeDiscount = l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = grossBeforeDiscount.subtract(l.getLineTotal()).setScale(2, RoundingMode.HALF_UP);
        return new InvoiceLineDto(
                l.getId(),
                l.getProduct() != null ? l.getProduct().getId() : null,
                l.getProductName(),
                l.getDescription(),
                l.getQuantity(),
                l.getUnitPrice(),
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
