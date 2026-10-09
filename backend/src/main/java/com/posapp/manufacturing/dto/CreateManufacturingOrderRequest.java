package com.posapp.manufacturing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

public class CreateManufacturingOrderRequest {

    @NotNull(message = "Bill of materials is required")
    private Long bomId;

    @NotNull(message = "Planned quantity is required")
    @Positive(message = "Planned quantity must be positive")
    private Integer quantityPlanned;

    private LocalDate scheduledDate;

    private String note;

    /** Nullable — see ManufacturingOrder.sourcePlanLineId's javadoc. Never set by the frontend directly; ProductionPlanService passes it internally when generating an order from a plan line. */
    private Long sourcePlanLineId;

    /** Overhead pools to apply to this order — see ManufacturingOrderOverhead's javadoc. */
    private List<Long> overheadCostIds;

    /** Multi-warehouse phase 4 — optional; where components are consumed from and finished goods land. Omitted = the primary warehouse's normal stock, as before. */
    private Long locationId;

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getBomId() { return bomId; }
    public void setBomId(Long bomId) { this.bomId = bomId; }
    public Integer getQuantityPlanned() { return quantityPlanned; }
    public void setQuantityPlanned(Integer quantityPlanned) { this.quantityPlanned = quantityPlanned; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getSourcePlanLineId() { return sourcePlanLineId; }
    public void setSourcePlanLineId(Long sourcePlanLineId) { this.sourcePlanLineId = sourcePlanLineId; }
    public List<Long> getOverheadCostIds() { return overheadCostIds; }
    public void setOverheadCostIds(List<Long> overheadCostIds) { this.overheadCostIds = overheadCostIds; }
}
