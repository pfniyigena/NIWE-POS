package com.posapp.manufacturing.dto;

import com.posapp.manufacturing.Workstation;

import java.math.BigDecimal;

public record WorkstationDto(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal hourlyRate,
        boolean active
) {
    public static WorkstationDto from(Workstation w) {
        return new WorkstationDto(w.getId(), w.getCode(), w.getName(), w.getDescription(), w.getHourlyRate(), w.isActive());
    }
}
