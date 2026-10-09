package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * "Restaurant Menu Category Profit" — the same grouping as {@link RestaurantMenuItemProfitRow}
 * one level up: by {@code MenuCategory} instead of item. See that record's javadoc for how
 * {@code cost} accounts for recipe-backed items' real ingredient cost rather than the item's own
 * (often unused) {@code unitCost}.
 */
public record RestaurantMenuCategoryProfitRow(
        Long categoryId,
        String categoryName,
        int quantitySold,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        BigDecimal marginPercent
) {
}
