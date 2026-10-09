package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * "Restaurant Menu Item Sales" — every checked-out order's own lines, grouped by menu item
 * (product), across every order type combined. Mirrors {@code ProductSalesRow} exactly, one
 * module over; {@code categoryName} is the item's {@code MenuCategory} (Starters, Mains,
 * Drinks...), resolved from {@code MenuItemRepository}, not the catalog {@code Product.category}
 * used by the POS-side report of the same shape — a restaurant menu item is organized by menu
 * category, which can differ from how the same product is categorized for retail. "Uncategorized"
 * for a product sold on a restaurant order with no matching {@code MenuItem} (e.g. rung before
 * being added to the menu, or later removed from it).
 */
public record RestaurantMenuItemSalesRow(
        Long productId,
        String sku,
        String itemName,
        String categoryName,
        int quantitySold,
        BigDecimal salesTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal
) {
}
