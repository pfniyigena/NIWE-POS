package com.posapp.manufacturing.dto;


import java.math.BigDecimal;import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ProduceManufacturingOrderRequest {

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    /** MF-4: units started that were rejected. Components and time are used for good + scrapped units. */
    @jakarta.validation.constraints.PositiveOrZero(message = "Scrapped quantity cannot be negative")
    private BigDecimal quantityScrapped;

    @jakarta.validation.constraints.Size(max = 255, message = "The scrap reason is too long")
    private String scrapReason;

    /** MF-4: optional actual quantity used of a component in this run (otherwise the recipe's quantity for the units started). */
    private java.util.List<ComponentQuantity> componentQuantities;

    public static class ComponentQuantity {
        private Long componentId;
        private BigDecimal quantity;

        public Long getComponentId() { return componentId; }
        public void setComponentId(Long componentId) { this.componentId = componentId; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    }

    public BigDecimal getQuantityScrapped() { return quantityScrapped; }
    public void setQuantityScrapped(BigDecimal quantityScrapped) { this.quantityScrapped = quantityScrapped; }
    public String getScrapReason() { return scrapReason; }
    public void setScrapReason(String scrapReason) { this.scrapReason = scrapReason; }
    public java.util.List<ComponentQuantity> getComponentQuantities() { return componentQuantities; }
    public void setComponentQuantities(java.util.List<ComponentQuantity> componentQuantities) { this.componentQuantities = componentQuantities; }

    /** MF-2: optional actual minutes for an operation in this run (by its step number); an operation not listed uses the planned minutes per unit x quantity. */
    private java.util.List<OperationMinutes> operationMinutes;

    public static class OperationMinutes {
        private Integer sequence;
        private java.math.BigDecimal minutes;

        public Integer getSequence() { return sequence; }
        public void setSequence(Integer sequence) { this.sequence = sequence; }
        public java.math.BigDecimal getMinutes() { return minutes; }
        public void setMinutes(java.math.BigDecimal minutes) { this.minutes = minutes; }
    }

    public java.util.List<OperationMinutes> getOperationMinutes() { return operationMinutes; }
    public void setOperationMinutes(java.util.List<OperationMinutes> operationMinutes) { this.operationMinutes = operationMinutes; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}
