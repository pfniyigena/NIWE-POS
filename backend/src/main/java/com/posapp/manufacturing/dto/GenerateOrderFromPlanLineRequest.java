package com.posapp.manufacturing.dto;


import java.math.BigDecimal;import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/** Converts one ProductionPlanLine into a real ManufacturingOrder — quantity defaults to the line's own suggestedQuantity client-side, but can be overridden here. */
public class GenerateOrderFromPlanLineRequest {

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    private LocalDate scheduledDate;

    private List<Long> overheadCostIds;

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public List<Long> getOverheadCostIds() { return overheadCostIds; }
    public void setOverheadCostIds(List<Long> overheadCostIds) { this.overheadCostIds = overheadCostIds; }
}
