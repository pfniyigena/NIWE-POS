package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * "Restaurant Sales by Server" — every checked-out order in the date range, grouped by {@code
 * RestaurantOrder.server} (who was serving that table/order) — the restaurant counterpart to
 * {@code ShiftSalesRow}'s "grouped by shift" shape, but by staff member instead, since a
 * restaurant order isn't tied to a till session the way a POS sale is. "Unassigned" for an order
 * checked out with no server ever set (server is optional — see {@code RestaurantOrder}'s own
 * javadoc).
 */
public record RestaurantServerSalesRow(
        Long serverId,
        String serverName,
        int orderCount,
        int quantitySold,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal
) {
}
