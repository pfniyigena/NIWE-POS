package com.posapp.salesorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;

/** Body for both creating and updating a DRAFT sales order — see SalesOrderService.createDraft/updateDraft. */
public class SalesOrderRequest {

    @NotNull(message = "Customer is required")
    private Long customerId;

    private Instant expectedDate;

    private String notes;

    /** Optional branch this order is sold from; null = head office (or unchanged when editing a draft). */
    private Long branchId;

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
    private java.math.BigDecimal discountPercent;

    /**
     * Fourier: "Global discount should be a fixed amount or percentage with percentage as the
     * default" — {@code "PERCENT"} (default when blank/null) or {@code "FIXED_AMOUNT"}; see
     * {@code GlobalDiscountType.parse}. Only meaningful in GLOBAL mode.
     */
    private String discountType;

    /**
     * Fourier: "Global discount should be a fixed amount or percentage" — the flat currency
     * amount, used only when {@link #discountType} is {@code "FIXED_AMOUNT"}; {@code
     * SalesOrderService} converts it to the equivalent {@link #discountPercent} once, at save
     * time, via {@code TaxMath.percentFromFixedAmount}.
     */
    @PositiveOrZero(message = "Discount amount cannot be negative")
    private java.math.BigDecimal discountFixedAmount;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<LineEntry> lines;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Instant getExpectedDate() { return expectedDate; }
    public void setExpectedDate(Instant expectedDate) { this.expectedDate = expectedDate; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public java.math.BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(java.math.BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public java.math.BigDecimal getDiscountFixedAmount() { return discountFixedAmount; }
    public void setDiscountFixedAmount(java.math.BigDecimal discountFixedAmount) { this.discountFixedAmount = discountFixedAmount; }
    public List<LineEntry> getLines() { return lines; }
    public void setLines(List<LineEntry> lines) { this.lines = lines; }

    public static class LineEntry {
        @NotNull(message = "Product is required")
        private Long productId;

        @Positive(message = "Quantity ordered must be greater than zero")
        private int quantityOrdered;

        @PositiveOrZero(message = "Unit price cannot be negative")
        private java.math.BigDecimal unitPrice;

        /**
         * Fourier: "Same for discount (but discount should be activate in the setting or
         * configuration) and it will be per line" — nullable; omitted/zero means no discount.
         * Validated and capped server-side by {@code StoreSettingsService.validateLineDiscount}
         * (including whether the feature is even turned on), not just this plain range check.
         */
        @PositiveOrZero(message = "Discount must be between 0 and 100")
        @jakarta.validation.constraints.DecimalMax(value = "100", message = "Discount must be between 0 and 100")
        private java.math.BigDecimal discountPercent;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public int getQuantityOrdered() { return quantityOrdered; }
        public void setQuantityOrdered(int quantityOrdered) { this.quantityOrdered = quantityOrdered; }
        public java.math.BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(java.math.BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public java.math.BigDecimal getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(java.math.BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    }
}
