package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Task: "Add Sale order... Then let's have Sale as Menu" — every Sales Order placed in the date
 * range (by {@code orderDate}, any status), same "record of what happened" shape as {@link
 * PurchaseOrderReportRow} on the purchasing side. {@code amountOutstanding} is zero for a DRAFT
 * order (nothing owed yet) or a CANCELLED one (never will be) — see {@code
 * CustomerCreditService.outstandingForSalesOrder}.
 */
public record SalesOrderReportRow(
        Long orderId,
        String orderNumber,
        Long customerId,
        String customerName,
        String status,
        Instant orderDate,
        Instant expectedDate,
        LocalDate dueDate,
        int lineCount,
        int quantityOrdered,
        int quantityFulfilled,
        BigDecimal totalAmount,
        BigDecimal amountOutstanding
) {
}
