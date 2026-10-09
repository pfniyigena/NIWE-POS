package com.posapp.packaging.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class PackagingBreakdownRequest {

    @NotNull(message = "Packaging rule is required")
    private Long ruleId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer parentQuantity;

    private String note;

    /** Multi-warehouse phase 4 — optional; the location the parent is taken from and the child put into. Omitted = the primary warehouse's normal stock, as before. */
    private Long locationId;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Integer getParentQuantity() { return parentQuantity; }
    public void setParentQuantity(Integer parentQuantity) { this.parentQuantity = parentQuantity; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
}
