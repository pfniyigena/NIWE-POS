package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class MenuItemRequest {
    @NotNull(message = "Category is required")
    private Long categoryId;
    @NotNull(message = "Product is required")
    private Long productId;
    private int sortOrder = 0;
    private boolean active = true;
    private BigDecimal priceOverride;
    private String station;
    private String allergens;
    private Integer defaultCourse;
    /** Minutes the kitchen should need; blank leaves it unchanged, 0 clears it (restaurant default). */
    private Integer prepMinutes;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public BigDecimal getPriceOverride() { return priceOverride; }
    public void setPriceOverride(BigDecimal priceOverride) { this.priceOverride = priceOverride; }
    public String getAllergens() { return allergens; }
    public void setAllergens(String allergens) { this.allergens = allergens; }
    public Integer getDefaultCourse() { return defaultCourse; }
    public Integer getPrepMinutes() { return prepMinutes; }
    public void setPrepMinutes(Integer prepMinutes) { this.prepMinutes = prepMinutes; }
    public void setDefaultCourse(Integer defaultCourse) { this.defaultCourse = defaultCourse; }
    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }
}
