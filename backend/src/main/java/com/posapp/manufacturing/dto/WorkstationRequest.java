package com.posapp.manufacturing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class WorkstationRequest {

    /** Only read on create — a workstation's code is stable for life, same convention as PaymentMethodEntity.code/TaxRate.code. */
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @PositiveOrZero(message = "Hourly rate cannot be negative")
    private BigDecimal hourlyRate;

    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
