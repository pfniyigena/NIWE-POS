package com.posapp.quotation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Body for both creating and updating a DRAFT quotation — see QuotationService.createDraft/updateDraft. */
public class QuotationRequest {

    @NotNull(message = "Customer is required")
    private Long customerId;

    private LocalDate validUntil;

    private String notes;

    /**
     * Fourier: "Except POS other module can have global discount. But again give the user to
     * configure both discount. Either on line or global" — a single discount percentage for the
     * whole quotation, nullable (omitted/zero means no discount). Validated and capped
     * server-side by {@code StoreSettingsService.validateGlobalDiscount} (including whether the
     * store's discount mode is actually GLOBAL), not just this plain range check. Mutually
     * exclusive with each {@link LineEntry#getDiscountPercent()} in practice — only one of the
     * two is ever accepted, depending on the store's current discount mode.
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
     * QuotationService} converts it to the equivalent {@link #discountPercent} once, at save
     * time, via {@code TaxMath.percentFromFixedAmount}.
     */
    @PositiveOrZero(message = "Discount amount cannot be negative")
    private BigDecimal discountFixedAmount;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<LineEntry> lines;

    /** Optional branch; null = head office (or unchanged when editing a draft). */
    private Long branchId;
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountFixedAmount() { return discountFixedAmount; }
    public void setDiscountFixedAmount(BigDecimal discountFixedAmount) { this.discountFixedAmount = discountFixedAmount; }
    public List<LineEntry> getLines() { return lines; }
    public void setLines(List<LineEntry> lines) { this.lines = lines; }

    public static class LineEntry {
        @NotNull(message = "Product is required")
        private Long productId;

        @Positive(message = "Quantity must be greater than zero")
        private int quantity;

        @PositiveOrZero(message = "Unit price cannot be negative")
        private BigDecimal unitPrice;

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
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    }
}
