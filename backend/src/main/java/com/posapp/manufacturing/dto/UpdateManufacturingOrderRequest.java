package com.posapp.manufacturing.dto;


import java.math.BigDecimal;import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * MF-3: editing a manufacturing order. {@code scheduledDate} and {@code note} replace what is there (null clears);
 * every other field left out (null) stays as it is. Recipe, indirect costs and location can only change while the order is a draft.
 */
public class UpdateManufacturingOrderRequest {

    private Long bomId;

    @Positive(message = "Planned quantity must be positive")
    private BigDecimal quantityPlanned;

    private LocalDate scheduledDate;

    @Size(max = 2000, message = "The note is too long")
    private String note;

    private List<Long> overheadCostIds;

    private Long locationId;

    public Long getBomId() { return bomId; }
    public void setBomId(Long bomId) { this.bomId = bomId; }
    public BigDecimal getQuantityPlanned() { return quantityPlanned; }
    public void setQuantityPlanned(BigDecimal quantityPlanned) { this.quantityPlanned = quantityPlanned; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<Long> getOverheadCostIds() { return overheadCostIds; }
    public void setOverheadCostIds(List<Long> overheadCostIds) { this.overheadCostIds = overheadCostIds; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
}
