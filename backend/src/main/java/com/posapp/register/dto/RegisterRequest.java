package com.posapp.register.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public class RegisterRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String location;
    /** Nullable — see {@code Register.warehouse}'s own javadoc for what null vs. a set value means. */
    private Long warehouseId;
    /** Nullable — see {@code Register.defaultLocation}'s own javadoc. Must belong to {@code warehouseId} when both are set (checked in RegisterService). */
    private Long defaultLocationId;

    /** Null means unchanged (switched on for a new register). */
    private Boolean active;
    /** Null clears it. */
    private BigDecimal defaultFloat;
    /** Null clears it. */
    private BigDecimal maxFloat;
    /** Null means unchanged; an empty list means anyone may work this till. */
    private List<Long> allowedCashierIds;

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public BigDecimal getDefaultFloat() { return defaultFloat; }
    public void setDefaultFloat(BigDecimal defaultFloat) { this.defaultFloat = defaultFloat; }
    public BigDecimal getMaxFloat() { return maxFloat; }
    public void setMaxFloat(BigDecimal maxFloat) { this.maxFloat = maxFloat; }
    public List<Long> getAllowedCashierIds() { return allowedCashierIds; }
    public void setAllowedCashierIds(List<Long> allowedCashierIds) { this.allowedCashierIds = allowedCashierIds; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getDefaultLocationId() { return defaultLocationId; }
    public void setDefaultLocationId(Long defaultLocationId) { this.defaultLocationId = defaultLocationId; }
}
