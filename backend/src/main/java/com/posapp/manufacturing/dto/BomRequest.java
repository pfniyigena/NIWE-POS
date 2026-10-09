package com.posapp.manufacturing.dto;

import jakarta.validation.Valid;
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

    public static class LineEntry {
        @NotNull(message = "Component product is required")
        private Long componentId;

        @NotNull(message = "Quantity per unit is required")
        @Positive(message = "Quantity per unit must be positive")
        private Integer quantityPerUnit;

        public Long getComponentId() { return componentId; }
        public void setComponentId(Long componentId) { this.componentId = componentId; }
        public Integer getQuantityPerUnit() { return quantityPerUnit; }
        public void setQuantityPerUnit(Integer quantityPerUnit) { this.quantityPerUnit = quantityPerUnit; }
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
