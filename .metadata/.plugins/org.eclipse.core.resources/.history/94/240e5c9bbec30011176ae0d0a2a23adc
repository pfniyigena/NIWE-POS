package com.posapp.order.dto;

import java.math.BigDecimal;

/** Both fields optional — send only the one you're changing (qty step vs. discount step in the UI). */
public class UpdateLineRequest {
    private Integer quantity;
    private BigDecimal discountPercent;

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }

    /** Serial-numbered products: the units being sold on this line. An empty list clears the choice. */
    private java.util.List<String> serialNumbers;
    public java.util.List<String> getSerialNumbers() { return serialNumbers; }
    public void setSerialNumbers(java.util.List<String> serialNumbers) { this.serialNumbers = serialNumbers; }
}
