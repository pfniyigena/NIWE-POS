package com.posapp.inventory.dto;

import jakarta.validation.constraints.NotNull;

/** Adds a product the sheet did not list (e.g. something found on the shelf that the system thinks is at zero). */
public class StockCountAddLineRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Location is required")
    private Long locationId;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
}
