package com.posapp.inventory.dto;

import jakarta.validation.constraints.NotNull;

public class StockCountRequest {

    @NotNull(message = "Warehouse is required")
    private Long warehouseId;

    /** Optional — null counts every category. */
    private Long categoryId;

    private String notes;

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
