package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotNull;

public class AddRestaurantLineRequest {
    @NotNull(message = "Menu item is required")
    private Long menuItemId;
    private int quantity = 1;
    private String notes;

    public Long getMenuItemId() { return menuItemId; }
    public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
