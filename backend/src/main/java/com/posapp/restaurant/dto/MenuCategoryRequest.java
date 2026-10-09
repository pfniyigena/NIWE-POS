package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public class MenuCategoryRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private int sortOrder = 0;
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
