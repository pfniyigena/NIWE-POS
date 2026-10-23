package com.posapp.packaging.dto;


import java.math.BigDecimal;import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class PackagingRuleRequest {

    @NotNull(message = "Parent product is required")
    private Long parentProductId;

    @NotNull(message = "Child product is required")
    private Long childProductId;

    @NotNull(message = "Units per parent is required")
    @Positive(message = "Units per parent must be positive")
    private BigDecimal unitsPerParent;

    private String notes;

    private boolean active = true;

    public Long getParentProductId() { return parentProductId; }
    public void setParentProductId(Long parentProductId) { this.parentProductId = parentProductId; }
    public Long getChildProductId() { return childProductId; }
    public void setChildProductId(Long childProductId) { this.childProductId = childProductId; }
    public BigDecimal getUnitsPerParent() { return unitsPerParent; }
    public void setUnitsPerParent(BigDecimal unitsPerParent) { this.unitsPerParent = unitsPerParent; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
