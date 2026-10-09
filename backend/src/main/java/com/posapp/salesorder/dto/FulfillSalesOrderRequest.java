package com.posapp.salesorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** Body for POST /api/sales-orders/{id}/fulfill — one or more lines' worth of stock actually going out, possibly a partial fulfillment. See SalesOrderService.fulfillLines. */
public class FulfillSalesOrderRequest {

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<FulfillLineEntry> lines;

    /**
     * Multi-warehouse phase 3 — the SALES location the goods leave from. Optional while the store's
     * multi-warehouse setting is off (null = the primary warehouse's normal stock, as always);
     * required, and must not be in the Main warehouse, once it is on — the same rule the POS applies.
     */
    private Long locationId;

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public List<FulfillLineEntry> getLines() { return lines; }
    public void setLines(List<FulfillLineEntry> lines) { this.lines = lines; }

    public static class FulfillLineEntry {
        @NotNull(message = "Sales order line is required")
        private Long lineId;

        @Positive(message = "Quantity fulfilled must be greater than zero")
        private int quantityFulfilled;

        /** Serial-numbered products: the units being delivered (one per unit). Empty = oldest in stock. */
        private List<String> serialNumbers;

        public List<String> getSerialNumbers() { return serialNumbers; }
        public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public int getQuantityFulfilled() { return quantityFulfilled; }
        public void setQuantityFulfilled(int quantityFulfilled) { this.quantityFulfilled = quantityFulfilled; }
    }
}
