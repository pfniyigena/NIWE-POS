package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * "Restaurant Menu Item Profit" — revenue (pre-tax) minus cost of goods, grouped by menu item.
 * {@code cost} is the *true* cost of selling that item, not simply its own {@code unitCost} ×
 * quantity: for a recipe-backed item (see {@code RestaurantOrderConsumption}), it's the sum of
 * every ingredient actually consumed for that line (quantity × the ingredient's own cost price at
 * checkout time), same figure {@code RestaurantOrderService#checkout} posts to Accounting as
 * COGS; a non-recipe item falls back to its own snapshotted {@code unitCost} × quantity, same as
 * {@code ProductProfitRow}. This is why the number here can differ sharply from what {@code
 * MenuItem.priceOverride}/the product's own cost price alone would suggest for a recipe dish.
 */
public record RestaurantMenuItemProfitRow(
        Long productId,
        String sku,
        String itemName,
        String categoryName,
        int quantitySold,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        BigDecimal marginPercent
) {
}
