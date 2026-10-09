package com.posapp.settings.dto;

/** Admin > Inventory > Settings (Task: "Inventory Setting (Example: allow negative stock)"). */
public class InventorySettingsRequest {

    /** See StoreSettings.allowNegativeStock's javadoc. */
    private boolean allowNegativeStock = true;

    /** See StoreSettings.productBreakdownEnabled's javadoc. */
    private boolean productBreakdownEnabled = false;

    /** Fourier: "Add allow multi-warehouse in inventory settings" — see StoreSettings.multiWarehouseEnabled's javadoc. */
    private boolean multiWarehouseEnabled = false;

    /** Multi-warehouse phase 4 — see StoreSettings.restaurantLocationId's javadoc. Null = primary warehouse stock. */
    private Long restaurantLocationId;

    /** WEIGHTED_AVERAGE (default), LAST_PURCHASE or MANUAL — see StoreSettings.costingMethod's javadoc. */
    private String costingMethod = "WEIGHTED_AVERAGE";

    public String getCostingMethod() { return costingMethod; }
    public void setCostingMethod(String costingMethod) { this.costingMethod = costingMethod; }

    public Long getRestaurantLocationId() { return restaurantLocationId; }
    public void setRestaurantLocationId(Long restaurantLocationId) { this.restaurantLocationId = restaurantLocationId; }

    public boolean isAllowNegativeStock() { return allowNegativeStock; }
    public void setAllowNegativeStock(boolean allowNegativeStock) { this.allowNegativeStock = allowNegativeStock; }

    public boolean isProductBreakdownEnabled() { return productBreakdownEnabled; }
    public void setProductBreakdownEnabled(boolean productBreakdownEnabled) { this.productBreakdownEnabled = productBreakdownEnabled; }

    public boolean isMultiWarehouseEnabled() { return multiWarehouseEnabled; }
    public void setMultiWarehouseEnabled(boolean multiWarehouseEnabled) { this.multiWarehouseEnabled = multiWarehouseEnabled; }
}
