package com.posapp.salesorder.dto;

import com.posapp.salesorder.SalesOrderReturnLine;

import java.math.BigDecimal;

public record SalesOrderReturnLineDto(
        Long id,
        Long orderLineId,
        String productName,
        int quantity,
        BigDecimal refundAmount
) {
    public static SalesOrderReturnLineDto from(SalesOrderReturnLine l) {
        return new SalesOrderReturnLineDto(
                l.getId(),
                l.getOrderLine() != null ? l.getOrderLine().getId() : null,
                l.getOrderLine() != null ? l.getOrderLine().getProductName() : null,
                l.getQuantity(),
                l.getRefundAmount()
        );
    }
}
