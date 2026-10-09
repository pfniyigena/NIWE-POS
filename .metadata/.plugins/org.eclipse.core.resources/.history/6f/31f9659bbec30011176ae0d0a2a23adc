package com.posapp.warehouse.dto;

import java.util.List;

/**
 * Phase 1 of multi-warehouse visibility: where one product's stock actually sits. {@code
 * sellableTotal} counts only active, non-DAMAGED locations (the same definition of "sellable" the
 * checkout and the warehouse-filtered reports use); {@code onHandTotal} is everything physically
 * recorded, including RECEIVING, STORAGE and DAMAGED stock. Read-only — nothing here moves stock.
 */
public record ProductStockByLocationDto(Long productId, int sellableTotal, int onHandTotal, List<Row> locations) {

    public record Row(Long warehouseId, String warehouseName, boolean warehousePrimary,
                      Long locationId, String locationName, String type, boolean active,
                      boolean sellable, int quantity) {
    }
}
