package com.posapp.inventory.dto;


import java.math.BigDecimal;/** One product at or below its reorder point. {@code onHand} is sellable stock across all warehouses; {@code onOrder} is the outstanding quantity on open purchase orders. */
public record ReorderSuggestionDto(
        Long productId,
        String sku,
        String productName,
        String categoryName,
        BigDecimal onHand,
        BigDecimal onOrder,
        BigDecimal reorderLevel,
        BigDecimal maxStockLevel,
        BigDecimal suggestedQuantity,
        /** v1.68.0: units promised to online orders nobody has accepted yet (already taken off onHand). */
        BigDecimal onlineOrders,
        /** v1.68.0: customers waiting for the product to come back (added to what is needed). */
        long waitingCustomers
) {
}
