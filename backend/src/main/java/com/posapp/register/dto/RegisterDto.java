package com.posapp.register.dto;

import com.posapp.register.Register;
import com.posapp.user.AppUser;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record RegisterDto(Long id, String name, String location, Long warehouseId, String warehouseName,
                           Long defaultLocationId, String defaultLocationName, Long openSessionId,
                           boolean active, BigDecimal defaultFloat, BigDecimal maxFloat,
                           List<Long> allowedCashierIds, List<String> allowedCashierNames,
                           boolean deviceBound, String deviceName, String deviceIp, java.time.Instant deviceBoundAt, String deviceBoundBy) {
    public static RegisterDto from(Register r, Long openSessionId) {
        List<AppUser> allowed = r.getAllowedCashiers() == null ? List.of()
                : r.getAllowedCashiers().stream().sorted(Comparator.comparing(u -> String.valueOf(u.getFullName()))).toList();
        return new RegisterDto(
                r.getId(),
                r.getName(),
                r.getLocation(),
                r.getWarehouse() != null ? r.getWarehouse().getId() : null,
                r.getWarehouse() != null ? r.getWarehouse().getName() : null,
                r.getDefaultLocation() != null ? r.getDefaultLocation().getId() : null,
                r.getDefaultLocation() != null ? r.getDefaultLocation().getName() : null,
                openSessionId,
                r.isActive(), r.getDefaultFloat(), r.getMaxFloat(),
                allowed.stream().map(AppUser::getId).toList(),
                allowed.stream().map(AppUser::getFullName).toList(),
                r.getDeviceId() != null, r.getDeviceName(), r.getDeviceIp(), r.getDeviceBoundAt(), r.getDeviceBoundBy());
    }
}
