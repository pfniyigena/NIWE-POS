package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Task 16: "Credit sale report" — one row per completed sale that had a credit portion, any customer, in the chosen date range. */
public record CreditSaleRow(
        Long orderId,
        String invoiceNumber,
        Instant completedAt,
        Long customerId,
        String customerName,
        BigDecimal creditAmount,
        BigDecimal amountOutstanding,
        LocalDate dueDate
) {
}
