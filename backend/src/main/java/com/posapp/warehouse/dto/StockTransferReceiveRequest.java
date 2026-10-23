package com.posapp.warehouse.dto;


import java.math.BigDecimal;import jakarta.validation.constraints.PositiveOrZero;

/** Receiving an in-transit inter-branch transfer: how many units actually arrived (may be fewer than sent). */
public class StockTransferReceiveRequest {

    @PositiveOrZero(message = "Received quantity cannot be negative")
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    private String note;

    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
