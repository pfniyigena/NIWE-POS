package com.posapp.order.dto;

import java.math.BigDecimal;

/** Every field optional — send only what you are changing (quantity, discount, serials, or the price). */
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

    /** Why a discount is being given. */
    private String discountReason;
    public String getDiscountReason() { return discountReason; }
    public void setDiscountReason(String discountReason) { this.discountReason = discountReason; }

    /** A manager's PIN, when the discount or the price change needs approval. */
    private String approverPin;
    public String getApproverPin() { return approverPin; }
    public void setApproverPin(String approverPin) { this.approverPin = approverPin; }

    /** Change the unit price by hand (a reason is needed and a manager approves), or put the normal price back. */
    private BigDecimal unitPrice;
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    private String priceReason;
    public String getPriceReason() { return priceReason; }
    public void setPriceReason(String priceReason) { this.priceReason = priceReason; }
    private Boolean resetPrice;
    public Boolean getResetPrice() { return resetPrice; }
    public void setResetPrice(Boolean resetPrice) { this.resetPrice = resetPrice; }
}
