package com.posapp.restaurant.dto;

import com.posapp.restaurant.RestaurantWastage;

import java.math.BigDecimal;
import java.time.Instant;

public record RestaurantWastageDto(
        Long id,
        Long productId,
        String productName,
        int quantity,
        String reason,
        String note,
        /** "PENDING" | "CONFIRMED" | "REJECTED" — see RestaurantWastageStatus's own javadoc. */
        String status,
        Integer stockBefore,
        Integer stockAfter,
        BigDecimal costImpact,
        Instant createdAt,
        /** Null until an admin confirms/rejects it. */
        Instant confirmedAt
) {
    public static RestaurantWastageDto from(RestaurantWastage w) {
        return new RestaurantWastageDto(
                w.getId(),
                w.getProduct() != null ? w.getProduct().getId() : null,
                w.getProductName(),
                w.getQuantity(),
                w.getReason() != null ? w.getReason().name() : null,
                w.getNote(),
                w.getStatus() != null ? w.getStatus().name() : null,
                w.getStockBefore(),
                w.getStockAfter(),
                w.getCostImpact(),
                w.getCreatedAt(),
                w.getConfirmedAt()
        );
    }
}
