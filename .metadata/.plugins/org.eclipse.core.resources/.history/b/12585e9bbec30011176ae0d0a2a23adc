package com.posapp.register.dto;

import com.posapp.register.Register;

public record RegisterDto(Long id, String name, String location, Long warehouseId, String warehouseName,
                           Long defaultLocationId, String defaultLocationName, Long openSessionId) {
    public static RegisterDto from(Register r, Long openSessionId) {
        return new RegisterDto(
                r.getId(),
                r.getName(),
                r.getLocation(),
                r.getWarehouse() != null ? r.getWarehouse().getId() : null,
                r.getWarehouse() != null ? r.getWarehouse().getName() : null,
                r.getDefaultLocation() != null ? r.getDefaultLocation().getId() : null,
                r.getDefaultLocation() != null ? r.getDefaultLocation().getName() : null,
                openSessionId);
    }
}
