package com.posapp.order.dto;

import com.posapp.order.OrderCalculator;
import com.posapp.order.OrderLine;

import java.math.BigDecimal;

public record OrderLineDto(
        Long id,
        Long productId,
        String description,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal discountPercent,
        BigDecimal lineTotal,
        String taxCode,
        BigDecimal taxRatePercent,
        BigDecimal taxTotal,
        boolean taxInclusive,
        /** Serial numbers typed for this line (one per text line), and the product's tracking mode (NONE/BATCH/SERIAL). */
        String serialNumbers,
        String trackingMode
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
                line.getProduct() != null && line.getProduct().getTrackingMode() != null ? line.getProduct().getTrackingMode().name() : "NONE"
        );
    }
}
