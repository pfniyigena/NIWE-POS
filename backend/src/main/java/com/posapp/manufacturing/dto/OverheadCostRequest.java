package com.posapp.manufacturing.dto;

import com.posapp.manufacturing.OverheadAllocationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class OverheadCostRequest {

    /** Only read on create — stable for life, same convention as Workstation.code. */
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Allocation method is required")
    private OverheadAllocationMethod allocationMethod;

    @NotNull(message = "Rate is required")
    @PositiveOrZero(message = "Rate cannot be negative")
    private BigDecimal rate;

    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public OverheadAllocationMethod getAllocationMethod() { return allocationMethod; }
    public void setAllocationMethod(OverheadAllocationMethod allocationMethod) { this.allocationMethod = allocationMethod; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
