package com.posapp.inventory.dto;

import jakarta.validation.constraints.NotNull;

public class StockAdjustmentRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    /** Positive (found more than recorded) or negative (loss/write-off) — never validated as nonzero here, since 0 is simply rejected by StockAdjustmentService with a clearer message than a bare constraint violation would give. */
    private int quantityDelta;

    @NotNull(message = "Reason is required")
    private String reason;

    private String note;

    /** Multi-warehouse phase 2 — optional; null/omitted keeps the legacy behaviour (the product's primary-warehouse stock figure). */
    private Long locationId;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(int quantityDelta) { this.quantityDelta = quantityDelta; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    /** BATCH products, increases: lot number and expiry. */
    private String batchNumber;
    private java.time.LocalDate expiryDate;
    /** SERIAL products: serial numbers added (increase) or removed (decrease). */
    private java.util.List<String> serialNumbers;

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public java.time.LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(java.time.LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public java.util.List<String> getSerialNumbers() { return serialNumbers; }
    public void setSerialNumbers(java.util.List<String> serialNumbers) { this.serialNumbers = serialNumbers; }
}
