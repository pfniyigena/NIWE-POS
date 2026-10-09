package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public class TableRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String section;
    private int capacity = 2;
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
