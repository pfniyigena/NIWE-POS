package com.posapp.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** Body for POST /api/purchase-orders/{id}/receive — one or more lines' worth of stock actually arriving, possibly a partial delivery. See PurchaseOrderService.receiveLines. */
public class ReceivePurchaseOrderRequest {

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<ReceiveLineEntry> lines;

    /** Optional extra cost of this delivery (freight, duty, clearing), spread over the delivered products by value and added to their cost. */
    @jakarta.validation.constraints.PositiveOrZero(message = "Landed cost cannot be negative")
    private java.math.BigDecimal landedCost;

    /** True if the landed cost was already paid in cash; otherwise it is recorded as owed. */
    private boolean landedCostPaid;

    private String landedCostNote;

    public java.math.BigDecimal getLandedCost() { return landedCost; }
    public void setLandedCost(java.math.BigDecimal landedCost) { this.landedCost = landedCost; }
    public boolean isLandedCostPaid() { return landedCostPaid; }
    public void setLandedCostPaid(boolean landedCostPaid) { this.landedCostPaid = landedCostPaid; }
    public String getLandedCostNote() { return landedCostNote; }
    public void setLandedCostNote(String landedCostNote) { this.landedCostNote = landedCostNote; }

    public List<ReceiveLineEntry> getLines() { return lines; }
    public void setLines(List<ReceiveLineEntry> lines) { this.lines = lines; }

    public static class ReceiveLineEntry {
        @NotNull(message = "Purchase order line is required")
        private Long lineId;

        @Positive(message = "Quantity received must be greater than zero")
        private int quantityReceived;

        /** BATCH products: the supplier's lot number (defaults to the order number). */
        private String batchNumber;

        /** BATCH products: when the lot expires (optional). */
        private java.time.LocalDate expiryDate;

        /** SERIAL products: one serial number per unit received. */
        private List<String> serialNumbers;

        public String getBatchNumber() { return batchNumber; }
        public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
        public java.time.LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(java.time.LocalDate expiryDate) { this.expiryDate = expiryDate; }
        public List<String> getSerialNumbers() { return serialNumbers; }
        public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public int getQuantityReceived() { return quantityReceived; }
        public void setQuantityReceived(int quantityReceived) { this.quantityReceived = quantityReceived; }
    }
}
