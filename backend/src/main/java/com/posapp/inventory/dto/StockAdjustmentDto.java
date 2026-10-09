package com.posapp.inventory.dto;

import com.posapp.inventory.StockAdjustment;

import java.time.Instant;

public record StockAdjustmentDto(
        Long id,
        Long productId,
        String productName,
        int quantityDelta,
        /** A preview until CONFIRMED, the real value once CONFIRMED — see StockAdjustment's own javadoc. */
        int stockBefore,
        int stockAfter,
        String reason,
        String note,
        /** "PENDING" | "CONFIRMED" | "REJECTED" — see StockAdjustmentStatus's own javadoc. */
        String status,
        Instant createdAt,
        /** Null until an admin (or a PERM_APPROVE_STOCK_ADJUSTMENT holder) confirms/rejects it. */
        Instant confirmedAt,
        /** Multi-warehouse phase 2 — null for an adjustment that targets the product's plain primary-warehouse stock (all legacy rows). */
        Long locationId,
        String locationName,
        String warehouseName
) {
    public static StockAdjustmentDto from(StockAdjustment a) {
        return new StockAdjustmentDto(
                a.getId(),
                a.getProduct() != null ? a.getProduct().getId() : null,
                a.getProductName(),
                a.getQuantityDelta(),
                a.getStockBefore(),
                a.getStockAfter(),
                a.getReason() != null ? a.getReason().name() : null,
                a.getNote(),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getCreatedAt(),
                a.getConfirmedAt(),
                a.getLocation() != null ? a.getLocation().getId() : null,
                a.getLocationName(),
                a.getWarehouseName()
        );
    }
}
