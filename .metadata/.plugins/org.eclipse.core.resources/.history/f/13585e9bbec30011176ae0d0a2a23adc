package com.posapp.register.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String location;
    /** Nullable — see {@code Register.warehouse}'s own javadoc for what null vs. a set value means. */
    private Long warehouseId;
    /** Nullable — see {@code Register.defaultLocation}'s own javadoc. Must belong to {@code warehouseId} when both are set (checked in RegisterService). */
    private Long defaultLocationId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getDefaultLocationId() { return defaultLocationId; }
    public void setDefaultLocationId(Long defaultLocationId) { this.defaultLocationId = defaultLocationId; }
}
