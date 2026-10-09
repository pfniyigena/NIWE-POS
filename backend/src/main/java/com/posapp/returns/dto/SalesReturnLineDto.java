package com.posapp.returns.dto;

import com.posapp.returns.SalesReturnLine;

import java.math.BigDecimal;

/** One returned line on a SalesReturn — new for the "print out the return" task, so a return receipt can list what was actually returned. */
public record SalesReturnLineDto(
        Long orderLineId,
        String description,
        int quantity,
        BigDecimal refundAmount
) {
    public static SalesReturnLineDto from(SalesReturnLine l) {
        var orderLine = l.getOrderLine();
        return new SalesReturnLineDto(
                orderLine != null ? orderLine.getId() : null,
                orderLine != null ? orderLine.getDescription() : null,
                l.getQuantity(),
                l.getRefundAmount()
        );
    }
}
