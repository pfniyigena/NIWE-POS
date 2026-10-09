package com.posapp.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** Body for both creating and updating a DRAFT purchase requisition — see PurchaseRequisitionService.createDraft/updateDraft. */
public class PurchaseRequisitionRequest {

    private String notes;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<LineEntry> lines;

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<LineEntry> getLines() { return lines; }
    public void setLines(List<LineEntry> lines) { this.lines = lines; }

    public static class LineEntry {
        @NotNull(message = "Product is required")
        private Long productId;

        @Positive(message = "Quantity requested must be greater than zero")
        private int quantityRequested;

        private String note;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public int getQuantityRequested() { return quantityRequested; }
        public void setQuantityRequested(int quantityRequested) { this.quantityRequested = quantityRequested; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}
