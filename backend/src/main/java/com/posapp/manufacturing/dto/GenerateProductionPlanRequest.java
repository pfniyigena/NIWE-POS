package com.posapp.manufacturing.dto;


import java.math.BigDecimal;import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class GenerateProductionPlanRequest {

    /** See ProductionPlan.lowStockThreshold's javadoc — same caller-supplied-threshold shape CatalogService.listLowStock already uses, rather than a stored per-product reorder point. */
    @NotNull(message = "Low stock threshold is required")
    @PositiveOrZero(message = "Low stock threshold cannot be negative")
    private BigDecimal lowStockThreshold;

    private String note;

    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(BigDecimal lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
