package com.posapp.warehouse.dto;

import com.posapp.warehouse.WarehouseLocation;

/**
 * Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — {@code type} replaces the earlier
 * {@code isDefault} boolean entirely. Sent as the enum's plain name ("RECEIVING", "SALES", ...)
 * so the frontend can drive a simple select without any translation table. A row mid-migration
 * (not yet backfilled) would read {@code null} here, but {@code WarehouseService} backfills every
 * row before any read path can observe one — see {@code WarehouseService#migrateLocationTypesIfNeeded}.
 */
public record WarehouseLocationDto(Long id, Long warehouseId, String name, boolean active, String type) {

    public static WarehouseLocationDto from(WarehouseLocation location) {
        return new WarehouseLocationDto(
                location.getId(),
                location.getWarehouse().getId(),
                location.getName(),
                location.isActive(),
                location.getType() != null ? location.getType().name() : null);
    }
}
