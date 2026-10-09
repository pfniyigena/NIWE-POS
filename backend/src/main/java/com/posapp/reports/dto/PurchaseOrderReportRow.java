package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Task 4: "Add Purchase order reports." One row per purchase order placed in the chosen date
 * range (by {@code orderDate}, regardless of current status — a cancelled order still shows up,
 * same "the report is a record of what happened, not just what's still active" stance every other
 * report here takes). {@code totalCost} is the sum of every line's {@code quantityOrdered *
 * unitCost} — what this order commits the store to paying, not what's actually been received so
 * far (that's {@code quantityReceived} instead, alongside {@code quantityOrdered} so a reader can
 * see at a glance how much of the order is still outstanding without opening it).
 */
public record PurchaseOrderReportRow(
        Long orderId,
        String orderNumber,
        Long supplierId,
        String supplierName,
        String status,
        Instant orderDate,
        Instant expectedDate,
        int lineCount,
        int quantityOrdered,
        int quantityReceived,
        BigDecimal totalCost,
        /** Task: "Add Supplier payment" — how much of {@code totalCost} has been paid off so far ({@code SupplierPaymentService.amountPaidForOrder}) and what's still owed ({@code amountOwedForOrder}, zero for a DRAFT/CANCELLED PO). */
        BigDecimal amountPaid,
        BigDecimal amountOutstanding
) {
}
