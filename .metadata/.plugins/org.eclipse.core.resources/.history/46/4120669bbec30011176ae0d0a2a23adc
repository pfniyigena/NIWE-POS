package com.posapp.warehouse.dto;

import com.posapp.warehouse.Warehouse;

public record WarehouseDto(Long id, String name, String address, boolean active, boolean primary,
                           /** Null = uses the store-wide default threshold. */
                           Integer lowStockThreshold,
                           /** Owning branch (null only transiently, before the startup backfill). */
                           Long branchId, String branchName) {

    public static WarehouseDto from(Warehouse warehouse) {
        return new WarehouseDto(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.isActive(),
                warehouse.isPrimary(),
                warehouse.getLowStockThreshold(),
                warehouse.getBranch() != null ? warehouse.getBranch().getId() : null,
                warehouse.getBranch() != null ? warehouse.getBranch().getName() : null);
    }
}
