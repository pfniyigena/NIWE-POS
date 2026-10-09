package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * "Restaurant Sales by Order Type" (Fourier: "Add all reports related to Restaurants Including
 * all type of orders") — every checked-out (CLOSED) restaurant order in the date range, grouped
 * by {@code RestaurantOrderType} (DINE_IN/TAKEAWAY/DELIVERY), so a manager can see at a glance
 * how revenue splits across the three ways a restaurant order can be taken. Mirrors {@code
 * CategorySalesRow}'s own sales/tax/grand-total shape, plus an order count column since "how many
 * orders of this type" is the natural first question here.
 */
public record RestaurantOrderTypeSalesRow(
        String orderType,
        int orderCount,
        int quantitySold,
        BigDecimal salesTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal
) {
}
