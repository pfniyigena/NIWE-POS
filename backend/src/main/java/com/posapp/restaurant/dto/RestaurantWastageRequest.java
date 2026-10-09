package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotNull;

public class RestaurantWastageRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    /** Always the positive amount wasted — never validated as positive here, since <= 0 is rejected by RestaurantWastageService with a clearer message than a bare constraint violation would give. */
    private int quantity;

    @NotNull(message = "Reason is required")
    private String reason;

    private String note;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
