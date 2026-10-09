package com.posapp.inventory.dto;

import com.posapp.inventory.PurchaseReturnLine;

import java.math.BigDecimal;

public record PurchaseReturnLineDto(
        Long id,
        Long orderLineId,
        String productName,
        int quantity,
        BigDecimal refundAmount
) {
    public static PurchaseReturnLineDto from(PurchaseReturnLine l) {
        return new PurchaseReturnLineDto(
                l.getId(),
                l.getOrderLine() != null ? l.getOrderLine().getId() : null,
                l.getOrderLine() != null ? l.getOrderLine().getProductName() : null,
                l.getQuantity(),
                l.getRefundAmount()
        );
    }
}
