package com.posapp.inventory.dto;

import com.posapp.inventory.StockCountLine;

import java.math.BigDecimal;

public record StockCountLineDto(
        Long id,
        Long productId,
        String sku,
        String productName,
        Long locationId,
        String locationName,
        int systemQuantity,
        Integer countedQuantity,
        /** counted - system; null while uncounted. */
        Integer variance,
        BigDecimal unitCost,
        /** variance x unitCost; null while uncounted. */
        BigDecimal varianceValue,
        Long adjustmentId
) {
    public static StockCountLineDto from(StockCountLine l) {
        Integer variance = l.getCountedQuantity() != null ? l.getCountedQuantity() - l.getSystemQuantity() : null;
        BigDecimal value = variance != null && l.getUnitCost() != null ? l.getUnitCost().multiply(BigDecimal.valueOf(variance)) : null;
        return new StockCountLineDto(
                l.getId(),
                l.getProduct() != null ? l.getProduct().getId() : null,
                l.getSku(), l.getProductName(),
                l.getLocation() != null ? l.getLocation().getId() : null,
                l.getLocationName(),
                l.getSystemQuantity(), l.getCountedQuantity(), variance,
                l.getUnitCost(), value, l.getAdjustmentId());
    }
}
