package com.posapp.tracking.dto;

import com.posapp.tracking.StockSerial;

import java.time.Instant;

public record SerialDto(
        Long id,
        Long productId,
        String sku,
        String productName,
        String serialNumber,
        String status,
        String warehouseName,
        String locationName,
        Instant receivedAt,
        String source,
        Instant soldAt,
        String soldRef
) {
    public static SerialDto from(StockSerial s) {
        return new SerialDto(
                s.getId(),
                s.getProduct() != null ? s.getProduct().getId() : null,
                s.getProduct() != null ? s.getProduct().getSku() : null,
                s.getProduct() != null ? s.getProduct().getName() : null,
                s.getSerialNumber(),
                s.getStatus() != null ? s.getStatus().name() : null,
                s.getLocation() != null && s.getLocation().getWarehouse() != null ? s.getLocation().getWarehouse().getName() : null,
                s.getLocation() != null ? s.getLocation().getName() : null,
                s.getReceivedAt(),
                s.getSource(),
                s.getSoldAt(),
                s.getSoldRef());
    }
}
