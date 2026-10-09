package com.posapp.warehouse.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Deliberately has no {@code primary} field — see Warehouse.primary's javadoc for why that flag
 * is never admin-settable through create/update, only ever assigned once by the startup seed.
 */
public record WarehouseRequest(@NotBlank String name, String address, Boolean active,
                               /** Optional per-warehouse low-stock threshold (0 or more); null = use the store-wide default. */
                               @jakarta.validation.constraints.PositiveOrZero Integer lowStockThreshold,
                               /** Optional owning branch; null = head office on create, unchanged on update. */
                               Long branchId) {
}
