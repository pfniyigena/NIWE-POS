package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotNull;

public class AddRestaurantLineRequest {
    @NotNull(message = "Menu item is required")
    private Long menuItemId;
    private int quantity = 1;
    private String notes;
    /** Options picked (ids from the item's option groups). */
    private java.util.List<Long> optionIds;
    /** Course 1 to 5; when empty the item's usual course is used. */
    private Integer course;
    /** Seat number at the table (optional). */
    private Integer seat;

    public Integer getSeat() { return seat; }
    public void setSeat(Integer seat) { this.seat = seat; }

    public java.util.List<Long> getOptionIds() { return optionIds; }
    public void setOptionIds(java.util.List<Long> optionIds) { this.optionIds = optionIds; }
    public Integer getCourse() { return course; }
    public void setCourse(Integer course) { this.course = course; }

    public Long getMenuItemId() { return menuItemId; }
    public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
