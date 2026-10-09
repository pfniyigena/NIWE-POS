package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Task 5: "Add Supplier Statement" — every purchase order placed with one specific supplier in
 * the chosen date range, oldest first (same shape/reasoning as {@link CustomerStatementRow}: no
 * {@code supplierId}/{@code supplierName} column here since the whole list is already scoped to
 * one supplier — see {@code ReportController.supplierStatement} — and the screen shows the
 * supplier's name once, in its own header, instead of repeating it on every row).
 */
public record SupplierStatementRow(
        Long orderId,
        String orderNumber,
        String status,
        Instant orderDate,
        Instant expectedDate,
        int lineCount,
        int quantityOrdered,
        int quantityReceived,
        BigDecimal totalCost,
        /** Task: "Add Supplier payment" — same paid/outstanding columns as PurchaseOrderReportRow. */
        BigDecimal amountPaid,
        BigDecimal amountOutstanding
) {
}
