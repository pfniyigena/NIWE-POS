package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * "Restaurant Orders" (detailed transactions) — one row per checked-out order, any {@code
 * RestaurantOrderType} (dine-in, takeaway, or delivery), the finest-grained restaurant sales
 * report, mirroring {@code ShiftTransactionRow}'s own "one row per completed sale" shape.
 * {@code tableOrCustomer} is {@code RestaurantOrder.displayLabel()} — the table name for dine-in,
 * else "Takeaway"/"Delivery — customer name".
 */
public record RestaurantOrderTransactionRow(
        Long orderId,
        String ticketNumber,
        String orderType,
        String tableOrCustomer,
        String serverName,
        Instant openedAt,
        Instant closedAt,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String paymentMethods
) {
}
