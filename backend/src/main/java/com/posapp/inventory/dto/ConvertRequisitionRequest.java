package com.posapp.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Body for POST /api/purchase-requisitions/{id}/convert — see
 * PurchaseRequisitionService.convertToPurchaseOrder. This is the one moment a requisition's
 * pricing/supplier commitment is made at all (see PurchaseRequisition's class javadoc): the admin
 * picks a supplier and prices each line here, and that becomes the resulting PurchaseOrder's own
 * supplier/unitCost — exactly the fields PurchaseOrderRequest itself asks for.
 */
public class ConvertRequisitionRequest {

    @NotNull(message = "Supplier is required")
    private Long supplierId;

    private Instant expectedDate;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<LineEntry> lines;

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public Instant getExpectedDate() { return expectedDate; }
    public void setExpectedDate(Instant expectedDate) { this.expectedDate = expectedDate; }
    public List<LineEntry> getLines() { return lines; }
    public void setLines(List<LineEntry> lines) { this.lines = lines; }

    public static class LineEntry {
        @NotNull(message = "Requisition line id is required")
        private Long lineId;

        @PositiveOrZero(message = "Unit cost cannot be negative")
        @NotNull(message = "Unit cost is required")
        private BigDecimal unitCost;

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public BigDecimal getUnitCost() { return unitCost; }
        public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    }
}
