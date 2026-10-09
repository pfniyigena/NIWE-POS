package com.posapp.order.dto;

import jakarta.validation.constraints.NotBlank;

public class AddByBarcodeRequest {
    @NotBlank(message = "Barcode is required")
    private String barcode;

    private int quantity = 1;

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
