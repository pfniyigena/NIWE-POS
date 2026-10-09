package com.posapp.recurringinvoice.dto;

import com.posapp.recurringinvoice.RecurrenceFrequency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Body for both creating and updating a {@code RecurringInvoiceTemplate} — see {@code RecurringInvoiceTemplateService.create}/{@code update}. Mirrors {@code com.posapp.invoice.dto.InvoiceRequest}'s own line/discount shape, plus the scheduling fields {@code com.posapp.recurringexpense.dto.RecurringExpenseTemplateRequest} already established. */
public class RecurringInvoiceTemplateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Customer is required")
    private Long customerId;

    @NotNull(message = "Frequency is required")
    private RecurrenceFrequency frequency = RecurrenceFrequency.MONTHLY;

    @NotNull(message = "First run date is required")
    private LocalDate nextRunDate;

    private Integer dueInDays;

    private String notes;

    @PositiveOrZero(message = "Discount must be between 0 and 100")
    @DecimalMax(value = "100", message = "Discount must be between 0 and 100")
    private BigDecimal discountPercent;

    private String discountType;

    @PositiveOrZero(message = "Discount amount cannot be negative")
    private BigDecimal discountFixedAmount;

    private boolean active = true;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<LineEntry> lines;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    /** Optional branch the generated invoices are issued from; null = head office (or unchanged when editing). */
    private Long branchId;
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public RecurrenceFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurrenceFrequency frequency) { this.frequency = frequency; }
    public LocalDate getNextRunDate() { return nextRunDate; }
    public void setNextRunDate(LocalDate nextRunDate) { this.nextRunDate = nextRunDate; }
    public Integer getDueInDays() { return dueInDays; }
    public void setDueInDays(Integer dueInDays) { this.dueInDays = dueInDays; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountFixedAmount() { return discountFixedAmount; }
    public void setDiscountFixedAmount(BigDecimal discountFixedAmount) { this.discountFixedAmount = discountFixedAmount; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<LineEntry> getLines() { return lines; }
    public void setLines(List<LineEntry> lines) { this.lines = lines; }

    public static class LineEntry {
        @NotNull(message = "Product is required")
        private Long productId;

        @Positive(message = "Quantity must be greater than zero")
        private int quantity;

        /** Unlike a standalone invoice line, this is required — the agreed recurring price, never re-derived from the live product. See RecurringInvoiceTemplateLine.unitPrice's own javadoc. */
        @NotNull(message = "Unit price is required")
        @PositiveOrZero(message = "Unit price cannot be negative")
        private BigDecimal unitPrice;

        private String description;

        @PositiveOrZero(message = "Discount must be between 0 and 100")
        @DecimalMax(value = "100", message = "Discount must be between 0 and 100")
        private BigDecimal discountPercent;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BigDecimal getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    }
}
