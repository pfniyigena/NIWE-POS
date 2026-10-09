package com.posapp.manufacturing.dto;

import com.posapp.manufacturing.OverheadAllocationMethod;
import com.posapp.manufacturing.OverheadCost;

import java.math.BigDecimal;

public record OverheadCostDto(
        Long id,
        String code,
        String name,
        OverheadAllocationMethod allocationMethod,
        BigDecimal rate,
        boolean active
) {
    public static OverheadCostDto from(OverheadCost o) {
        return new OverheadCostDto(o.getId(), o.getCode(), o.getName(), o.getAllocationMethod(), o.getRate(), o.isActive());
    }
}
