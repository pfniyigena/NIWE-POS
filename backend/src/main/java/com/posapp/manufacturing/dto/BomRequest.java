package com.posapp.manufacturing.dto;


import java.math.BigDecimal;import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class BomRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    private String name;

    /** Nullable — see Bom.routing's javadoc. */
    private Long routingId;

    private boolean active = true;

    @NotEmpty(message = "A bill of materials needs at least one component line")
    @Valid
    private List<LineEntry> lines;

    /** MF-4: optional by-products. */
    @Valid
    private List<ByProductEntry> byProducts;

    public static class ByProductEntry {
        @NotNull(message = "By-product is required")
        private Long productId;

        @NotNull(message = "By-product quantity per unit is required")
        @Positive(message = "By-product quantity per unit must be positive")
        private BigDecimal quantityPerUnit;

        @jakarta.validation.constraints.DecimalMin(value = "0", message = "The cost share cannot be negative")
        @jakarta.validation.constraints.DecimalMax(value = "100", message = "The cost share cannot be more than 100%")
        private java.math.BigDecimal costSharePercent;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public BigDecimal getQuantityPerUnit() { return quantityPerUnit; }
        public void setQuantityPerUnit(BigDecimal quantityPerUnit) { this.quantityPerUnit = quantityPerUnit; }
        public java.math.BigDecimal getCostSharePercent() { return costSharePercent; }
        public void setCostSharePercent(java.math.BigDecimal costSharePercent) { this.costSharePercent = costSharePercent; }
    }

    public List<ByProductEntry> getByProducts() { return byProducts; }
    public void setByProducts(List<ByProductEntry> byProducts) { this.byProducts = byProducts; }

    public static class LineEntry {
        @NotNull(message = "Component product is required")
        private Long componentId;

        @NotNull(message = "Quantity per unit is required")
        @Positive(message = "Quantity per unit must be positive")
        private BigDecimal quantityPerUnit;

        public Long getComponentId() { return componentId; }
        public void setComponentId(Long componentId) { this.componentId = componentId; }
        public BigDecimal getQuantityPerUnit() { return quantityPerUnit; }
        public void setQuantityPerUnit(BigDecimal quantityPerUnit) { this.quantityPerUnit = quantityPerUnit; }
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getRoutingId() { return routingId; }
    public void setRoutingId(Long routingId) { this.routingId = routingId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<LineEntry> getLines() { return lines; }
    public void setLines(List<LineEntry> lines) { this.lines = lines; }
}
