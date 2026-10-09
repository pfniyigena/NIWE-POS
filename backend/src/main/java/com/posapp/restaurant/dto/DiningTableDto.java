package com.posapp.restaurant.dto;

import com.posapp.restaurant.DiningTable;

public record DiningTableDto(
        Long id,
        String name,
        String section,
        int capacity,
        String status,
        boolean active,
        /** Null when no order is currently open at this table. */
        Long openOrderId
) {
    public static DiningTableDto from(DiningTable t, Long openOrderId) {
        return new DiningTableDto(t.getId(), t.getName(), t.getSection(), t.getCapacity(), t.getStatus().name(), t.isActive(), openOrderId);
    }
}
