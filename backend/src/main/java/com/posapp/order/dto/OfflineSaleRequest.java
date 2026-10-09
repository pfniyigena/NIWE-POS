package com.posapp.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A whole sale rung on the terminal while offline, replayed here in one shot once connectivity
 * returns — Task: "local queuing of sales that sync once connectivity returns". Deliberately one
 * request rather than the usual create-order / add-line / add-line / checkout round trips: those
 * each hit the server for pricing/tax, which is exactly what isn't available offline. The terminal
 * builds this whole thing client-side from its cached product catalog (see
 * CatalogController.listForOfflineCache) using a simplified local estimate of the same math
 * OrderCalculator does, then this one call replays it for real — the server, not the terminal's
 * offline estimate, is always the authority on the final price/tax/total. See
 * OrderService.syncOfflineSale.
 */
public class OfflineSaleRequest {

    /**
     * A UUID the terminal generates once, when the sale is first rung offline (not at sync time) —
     * the idempotency key. If this exact sync request is ever sent twice (the terminal retried
     * after a timeout, or after losing connectivity again mid-request before seeing the response),
     * the second attempt finds the already-created order by this id and returns it rather than
     * double-selling. See SaleOrder.clientTempId.
     */
    @NotBlank(message = "clientTempId is required")
    private String clientTempId;

    @NotNull(message = "cashierSessionId is required")
    private Long cashierSessionId;

    /** Null for a walk-in customer, same as the online flow. */
    private Long customerId;

    /**
     * When the cashier actually rang this up, offline — the terminal's own clock at the time, not
     * whenever this request happens to reach the server. See SaleOrder.offlineQueuedAt.
     */
    @NotNull(message = "queuedAt is required")
    private Instant queuedAt;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<LineEntry> lines;

    @NotEmpty(message = "At least one payment is required")
    @Valid
    private List<CheckoutRequest.PaymentEntry> payments;

    /** Same meaning as CheckoutRequest.dueDate — only used when a payment entry is credit-flagged. */
    private LocalDate dueDate;

    /**
     * What the terminal's own offline estimate showed as the grand total, from its simplified
     * local price/tax math against possibly-stale cached product data. Compared against the real,
     * server-computed total once this sale is fully replayed; a material mismatch is recorded on
     * SaleOrder.syncNote for later review rather than silently accepted or rejected. Optional —
     * null just means no mismatch is ever flagged for this sale.
     */
    private BigDecimal clientEstimatedTotal;

    public String getClientTempId() { return clientTempId; }
    public void setClientTempId(String clientTempId) { this.clientTempId = clientTempId; }
    public Long getCashierSessionId() { return cashierSessionId; }
    public void setCashierSessionId(Long cashierSessionId) { this.cashierSessionId = cashierSessionId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Instant getQueuedAt() { return queuedAt; }
    public void setQueuedAt(Instant queuedAt) { this.queuedAt = queuedAt; }
    public List<LineEntry> getLines() { return lines; }
    public void setLines(List<LineEntry> lines) { this.lines = lines; }
    public List<CheckoutRequest.PaymentEntry> getPayments() { return payments; }
    public void setPayments(List<CheckoutRequest.PaymentEntry> payments) { this.payments = payments; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getClientEstimatedTotal() { return clientEstimatedTotal; }
    public void setClientEstimatedTotal(BigDecimal clientEstimatedTotal) { this.clientEstimatedTotal = clientEstimatedTotal; }

    public static class LineEntry {
        @NotNull(message = "productId is required")
        private Long productId;

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be at least 1")
        private Integer quantity;

        /** Same 0-100 validation as UpdateLineRequest.discountPercent — null/omitted means no discount. */
        private BigDecimal discountPercent;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    }
}
