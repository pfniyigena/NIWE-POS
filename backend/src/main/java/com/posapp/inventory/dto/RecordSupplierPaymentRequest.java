package com.posapp.inventory.dto;

import com.posapp.inventory.PayableType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * Recording a supplier payment ("Add Supplier payment") — the accounts-payable mirror of {@code
 * com.posapp.credit.dto.RecordPaymentRequest}. {@code allocations} must sum to exactly {@code
 * amount} — see {@code SupplierPaymentService.recordPayment}'s validation.
 */
public class RecordSupplierPaymentRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String note;

    @NotEmpty(message = "At least one purchase order allocation is required")
    @Valid
    private List<AllocationEntry> allocations;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<AllocationEntry> getAllocations() { return allocations; }
    public void setAllocations(List<AllocationEntry> allocations) { this.allocations = allocations; }

    public static class AllocationEntry {
        @NotNull(message = "Purchase order id is required")
        private Long purchaseOrderId;

        /**
         * Fourier: "The Purchase order should flow same workflow as Sale order" — which kind of
         * document {@link #purchaseOrderId} refers to; see {@code PayableType}'s own javadoc.
         * Defaults to {@code PURCHASE_ORDER} when omitted, so existing callers/tests written
         * before Bills existed keep working unchanged, same "default to the old shape" convention
         * {@code RecordPaymentRequest.AllocationEntry.orderType} already follows on the customer
         * side.
         */
        private PayableType payableType = PayableType.PURCHASE_ORDER;

        @NotNull(message = "Allocation amount is required")
        @Positive(message = "Allocation amount must be greater than zero")
        private BigDecimal amount;

        public Long getPurchaseOrderId() { return purchaseOrderId; }
        public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
        public PayableType getPayableType() { return payableType; }
        public void setPayableType(PayableType payableType) { this.payableType = payableType; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }
}
