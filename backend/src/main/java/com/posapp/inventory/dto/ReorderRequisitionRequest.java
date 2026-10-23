package com.posapp.inventory.dto;


import java.math.BigDecimal;import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class ReorderRequisitionRequest {

    private String notes;

    @NotEmpty(message = "Select at least one product")
    @Valid
    private List<Entry> lines;

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<Entry> getLines() { return lines; }
    public void setLines(List<Entry> lines) { this.lines = lines; }

    public static class Entry {
        @NotNull(message = "Product is required")
        private Long productId;

        @Positive(message = "Quantity must be greater than zero")
        private BigDecimal quantity = BigDecimal.ZERO;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    }
}
