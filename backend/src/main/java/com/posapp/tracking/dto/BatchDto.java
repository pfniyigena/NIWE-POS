package com.posapp.tracking.dto;

import com.posapp.tracking.StockBatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record BatchDto(
        Long id,
        Long productId,
        String sku,
        String productName,
        String warehouseName,
        String locationName,
        boolean inTransit,
        String batchNumber,
        LocalDate expiryDate,
        Long daysToExpiry,
        int quantity,
        BigDecimal unitCost,
        String source
) {
    public static BatchDto from(StockBatch b) {
        Long days = b.getExpiryDate() != null ? ChronoUnit.DAYS.between(LocalDate.now(), b.getExpiryDate()) : null;
        return new BatchDto(
                b.getId(),
                b.getProduct() != null ? b.getProduct().getId() : null,
                b.getProduct() != null ? b.getProduct().getSku() : null,
                b.getProduct() != null ? b.getProduct().getName() : null,
                b.getLocation() != null && b.getLocation().getWarehouse() != null ? b.getLocation().getWarehouse().getName() : null,
                b.getLocation() != null ? b.getLocation().getName() : null,
                b.getLocation() == null,
                b.getBatchNumber(),
                b.getExpiryDate(),
                days,
                b.getQuantity(),
                b.getUnitCost(),
                b.getSource());
    }
}
