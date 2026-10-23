package com.posapp.order.dto;

import com.posapp.order.OrderCalculator;
import com.posapp.order.OrderLine;

import java.math.BigDecimal;

public record OrderLineDto(
        Long id,
        Long productId,
        String description,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal discountPercent,
        BigDecimal lineTotal,
        String taxCode,
        BigDecimal taxRatePercent,
        BigDecimal taxTotal,
        boolean taxInclusive,
        /** Serial numbers typed for this line (one per text line), and the product's tracking mode (NONE/BATCH/SERIAL). */
        String serialNumbers,
        String trackingMode,
        /** The normal price, and the price list the price came from when it differs; null on older sales. */
        BigDecimal listPrice,
        String priceNote,
        /** What promotions took off this line (percent, after the cashier's discount) and which promotions. */
        BigDecimal promoPercent,
        String promoNote,
        /** Why the discount was given and who approved it; whether the price was changed by hand and what it was. */
        String discountReason,
        String discountApprovedBy,
        boolean priceOverridden,
        BigDecimal originalUnitPrice,
        String priceOverrideReason
) {
    public static OrderLineDto from(OrderLine line) {
        return new OrderLineDto(
                line.getId(),
                line.getProduct() != null ? line.getProduct().getId() : null,
                line.getDescription(),
                line.getUnitPrice(),
                line.getQuantity(),
                line.getDiscountPercent(),
                OrderCalculator.lineTotal(line),
                line.getTaxCode(),
                line.getTaxRatePercent(),
                OrderCalculator.lineTax(line),
                Boolean.TRUE.equals(line.getTaxInclusive()),
                line.getSerialNumbers(),
                line.getProduct() != null && line.getProduct().getTrackingMode() != null ? line.getProduct().getTrackingMode().name() : "NONE",
                line.getListPrice(),
                line.getPriceNote(),
                line.getPromoPercent(),
                line.getPromoNote(),
                line.getDiscountReason(),
                line.getDiscountApprovedBy(),
                line.isPriceOverridden(),
                line.getOriginalUnitPrice(),
                line.getPriceOverrideReason()
        );
    }
}
