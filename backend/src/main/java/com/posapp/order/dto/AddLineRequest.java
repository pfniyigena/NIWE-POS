package com.posapp.order.dto;


import java.math.BigDecimal;import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class AddLineRequest {
    @NotNull(message = "Product is required")
    private Long productId;

    @jakarta.validation.constraints.Positive(message = "Quantity must be more than zero")
    private BigDecimal quantity = BigDecimal.ONE;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}
