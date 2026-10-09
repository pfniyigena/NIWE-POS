package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * Task 1: "Inventory Report" — "Stock on hand & valuation" half of the user's "Both" answer. One
 * row per active product: how much is on the shelf right now and what it's worth at cost
 * ({@code stockValue = stockQuantity * unitCost}, the standard "what would it cost to replace
 * this inventory" figure — never at {@code unitPrice}, which is what a customer pays, not what
 * this stock is carried at). A snapshot, like Outstanding/Overdue Credit above — there's no date
 * range for "what do I have right now."
 */
public record StockValuationRow(
        Long productId,
        String sku,
        String productName,
        String categoryName,
        int stockQuantity,
        BigDecimal unitCost,
        BigDecimal stockValue,
        /** "Low stock" (below {@code app.pos.low-stock-threshold}, the same config CatalogService.listLowStock/the dashboard use) or "OK". */
        String status,
        /** Quantity promised on confirmed / part-delivered sales orders (not subtracted from {@code stockQuantity}). */
        int reserved,
        /** Units on the road between branches (dispatched, not yet received) — not part of {@code stockQuantity}. */
        int inTransit
) {
}
