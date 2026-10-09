package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Task 11: "Overdue credit report" — snapshot-style, no date range: one row per still-outstanding
 * credit sale whose due date has already passed. A sale with no due date at all can never appear
 * here (see SaleOrder.dueDate's javadoc — it's a deliberate "never overdue" case).
 */
public record OverdueCreditRow(
        Long orderId,
        String invoiceNumber,
        Long customerId,
        String customerName,
        String phone,
        LocalDate dueDate,
        long daysOverdue,
        BigDecimal creditAmount,
        BigDecimal amountOutstanding
) {
}
