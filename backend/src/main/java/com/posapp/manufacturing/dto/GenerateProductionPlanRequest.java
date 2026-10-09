package com.posapp.manufacturing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class GenerateProductionPlanRequest {

    /** See ProductionPlan.lowStockThreshold's javadoc — same caller-supplied-threshold shape CatalogService.listLowStock already uses, rather than a stored per-product reorder point. */
    @NotNull(message = "Low stock threshold is required")
    @PositiveOrZero(message = "Low stock threshold cannot be negative")
    private Integer lowStockThreshold;

    private String note;

    public Integer getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(Integer lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
