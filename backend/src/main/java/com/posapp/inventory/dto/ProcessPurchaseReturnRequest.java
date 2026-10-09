package com.posapp.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** Body for POST /api/purchase-returns. See PurchaseReturnService.processReturn. */
public class ProcessPurchaseReturnRequest {

    @NotNull(message = "Purchase order is required")
    private Long purchaseOrderId;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<ReturnLineEntry> lines;

    /** Optional — same as ProcessSalesOrderReturnRequest.reason. */
    private String reason;

    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public List<ReturnLineEntry> getLines() { return lines; }
    public void setLines(List<ReturnLineEntry> lines) { this.lines = lines; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public static class ReturnLineEntry {
        @NotNull(message = "Purchase order line is required")
        private Long lineId;

        @Positive(message = "Quantity must be greater than zero")
        private int quantity;

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
