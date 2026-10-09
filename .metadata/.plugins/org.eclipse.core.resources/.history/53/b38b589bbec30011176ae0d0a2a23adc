package com.posapp.inventory.dto;

/** One product at or below its reorder point. {@code onHand} is sellable stock across all warehouses; {@code onOrder} is the outstanding quantity on open purchase orders. */
public record ReorderSuggestionDto(
        Long productId,
        String sku,
        String productName,
        String categoryName,
        int onHand,
        int onOrder,
        int reorderLevel,
        int maxStockLevel,
        int suggestedQuantity
) {
}
