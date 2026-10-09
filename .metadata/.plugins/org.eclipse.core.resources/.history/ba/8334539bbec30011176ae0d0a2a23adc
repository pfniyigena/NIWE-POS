package com.posapp.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class ProductRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String sku;
    private String barcode;

    /** Nullable, but must be unique if set — see Product.code's javadoc. Blank is normalized to null in CatalogService. */
    private String code;

    /** Nullable data: URL — see Product.iconDataUrl's javadoc. */
    private String iconDataUrl;

    /** Nullable free text — see Product.description's javadoc. */
    private String description;

    @NotNull(message = "Unit price is required")
    @PositiveOrZero(message = "Unit price cannot be negative")
    private BigDecimal unitPrice;

    /** Nullable — treated as zero (no cost recorded yet) if omitted. See Product.costPrice. */
    @PositiveOrZero(message = "Cost price cannot be negative")
    private BigDecimal costPrice;

    @PositiveOrZero(message = "Stock quantity cannot be negative")
    private int stockQuantity;

    /** Defaults to true (every existing product keeps tracking stock) — see Product.trackStock's javadoc. */
    private boolean trackStock = true;

    private Long categoryId;

    /** Nullable — see Product.brand's javadoc. */
    private Long brandId;

    /** Nullable — a product with no tax rate assigned is tax-exempt. See Product.taxRate. */
    private Long taxRateId;

    private boolean active = true;

    /** Nullable — see Product.unspCode's javadoc. */
    private String unspCode;

    /** Defaults to false (goods) — see Product.service's javadoc. */
    private boolean service = false;

    /** Optional reorder point / order-up-to level — see Product.reorderLevel. */
    @PositiveOrZero(message = "Reorder level cannot be negative")
    private Integer reorderLevel;

    @PositiveOrZero(message = "Maximum stock level cannot be negative")
    private Integer maxStockLevel;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getIconDataUrl() { return iconDataUrl; }
    public void setIconDataUrl(String iconDataUrl) { this.iconDataUrl = iconDataUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public boolean isTrackStock() { return trackStock; }
    public void setTrackStock(boolean trackStock) { this.trackStock = trackStock; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }
    public Long getTaxRateId() { return taxRateId; }
    public void setTaxRateId(Long taxRateId) { this.taxRateId = taxRateId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getUnspCode() { return unspCode; }
    public void setUnspCode(String unspCode) { this.unspCode = unspCode; }
    public boolean isService() { return service; }
    public void setService(boolean service) { this.service = service; }
    /** NONE (default), BATCH or SERIAL. */
    private String trackingMode;
    public String getTrackingMode() { return trackingMode; }
    public void setTrackingMode(String trackingMode) { this.trackingMode = trackingMode; }

    public Integer getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(Integer reorderLevel) { this.reorderLevel = reorderLevel; }
    public Integer getMaxStockLevel() { return maxStockLevel; }
    public void setMaxStockLevel(Integer maxStockLevel) { this.maxStockLevel = maxStockLevel; }
}
