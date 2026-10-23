package com.posapp.order.dto;


import java.math.BigDecimal;import jakarta.validation.constraints.NotBlank;

public class AddByBarcodeRequest {
    @NotBlank(message = "Barcode is required")
    private String barcode;

    private BigDecimal quantity = BigDecimal.ONE;

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}
