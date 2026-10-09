package com.posapp.salesorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** Body for POST /api/sales-order-returns. See SalesOrderReturnService.processReturn. */
public class ProcessSalesOrderReturnRequest {

    @NotNull(message = "Sales order is required")
    private Long salesOrderId;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<ReturnLineEntry> lines;

    /** Optional — same as ProcessReturnRequest.reason on the POS side. */
    private String reason;

    /** Multi-warehouse phase 3 — optional; where the returned goods go back. Omitted = the location the order was last fulfilled from (primary warehouse stock for older orders). */
    private Long locationId;

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getSalesOrderId() { return salesOrderId; }
    public void setSalesOrderId(Long salesOrderId) { this.salesOrderId = salesOrderId; }
    public List<ReturnLineEntry> getLines() { return lines; }
    public void setLines(List<ReturnLineEntry> lines) { this.lines = lines; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public static class ReturnLineEntry {
        @NotNull(message = "Sales order line is required")
        private Long lineId;

        @Positive(message = "Quantity must be greater than zero")
        private int quantity;

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
