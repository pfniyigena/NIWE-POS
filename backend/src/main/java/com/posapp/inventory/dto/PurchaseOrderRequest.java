package com.posapp.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Body for both creating and updating a DRAFT purchase order — see PurchaseOrderService.createDraft/updateDraft. */
public class PurchaseOrderRequest {

    @NotNull(message = "Supplier is required")
    private Long supplierId;

    private Instant expectedDate;

    private String notes;

    /**
     * Fourier: "Except POS other module can have global discount. But again give the user to
     * configure both discount. Either on line or global" — a single discount percentage for the
     * whole order, nullable (omitted/zero means no discount). Validated and capped server-side
     * by {@code StoreSettingsService.validateGlobalDiscount} (including whether the store's
     * discount mode is actually GLOBAL), not just this plain range check. Mutually exclusive
     * with each {@link LineEntry#getDiscountPercent()} in practice — only one of the two is ever
     * accepted, depending on the store's current discount mode.
     */
    @PositiveOrZero(message = "Discount must be between 0 and 100")
    @jakarta.validation.constraints.DecimalMax(value = "100", message = "Discount must be between 0 and 100")
    private BigDecimal discountPercent;

    /**
     * Fourier: "Global discount should be a fixed amount or percentage with percentage as the
     * default" — {@code "PERCENT"} (default when blank/null) or {@code "FIXED_AMOUNT"}; see
     * {@code GlobalDiscountType.parse}. Only meaningful in GLOBAL mode.
     */
    private String discountType;

    /**
     * Fourier: "Global discount should be a fixed amount or percentage" — the flat currency
     * amount, used only when {@link #discountType} is {@code "FIXED_AMOUNT"}; {@code
     * PurchaseOrderService} converts it to the equivalent {@link #discountPercent} once, at
     * save time, via {@code TaxMath.percentFromFixedAmount}.
     */
    @PositiveOrZero(message = "Discount amount cannot be negative")
    private BigDecimal discountFixedAmount;

    /** Multi-warehouse phase 2 — optional; null/omitted means the primary warehouse. See PurchaseOrder.receivingWarehouse. */
    private Long receivingWarehouseId;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<LineEntry> lines;

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public Instant getExpectedDate() { return expectedDate; }
    public void setExpectedDate(Instant expectedDate) { this.expectedDate = expectedDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountFixedAmount() { return discountFixedAmount; }
    public void setDiscountFixedAmount(BigDecimal discountFixedAmount) { this.discountFixedAmount = discountFixedAmount; }
    public Long getReceivingWarehouseId() { return receivingWarehouseId; }
    public void setReceivingWarehouseId(Long receivingWarehouseId) { this.receivingWarehouseId = receivingWarehouseId; }
    public List<LineEntry> getLines() { return lines; }
    public void setLines(List<LineEntry> lines) { this.lines = lines; }

    public static class LineEntry {
        @NotNull(message = "Product is required")
        private Long productId;

        @Positive(message = "Quantity ordered must be greater than zero")
        private int quantityOrdered;

        @PositiveOrZero(message = "Unit cost cannot be negative")
        private BigDecimal unitCost;

        /**
         * Fourier: "Same for discount (but discount should be activate in the setting or
         * configuration) and it will be per line" — nullable; omitted/zero means no discount.
         * Validated and capped server-side by {@code StoreSettingsService.validateLineDiscount}
         * (including whether the feature is even turned on), not just this plain range check.
         */
        @PositiveOrZero(message = "Discount must be between 0 and 100")
        @jakarta.validation.constraints.DecimalMax(value = "100", message = "Discount must be between 0 and 100")
        private BigDecimal discountPercent;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public int getQuantityOrdered() { return quantityOrdered; }
        public void setQuantityOrdered(int quantityOrdered) { this.quantityOrdered = quantityOrdered; }
        public BigDecimal getUnitCost() { return unitCost; }
        public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
        public BigDecimal getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    }
}
