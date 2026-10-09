package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * "Restaurant Menu Category Sales" — the same grouping as {@link RestaurantMenuItemSalesRow} one
 * level up: every checked-out order's lines, grouped by {@code MenuCategory} instead of item.
 */
public record RestaurantMenuCategorySalesRow(
        Long categoryId,
        String categoryName,
        int quantitySold,
        BigDecimal salesTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal
) {
}
