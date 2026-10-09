package com.posapp.accounting.dto;

import com.posapp.accounting.CostCenter;

public record CostCenterDto(
        Long id,
        String code,
        String name,
        boolean active
) {
    public static CostCenterDto from(CostCenter c) {
        return new CostCenterDto(c.getId(), c.getCode(), c.getName(), c.isActive());
    }
}
