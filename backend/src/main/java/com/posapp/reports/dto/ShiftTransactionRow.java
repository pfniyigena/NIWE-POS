package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of "Detailed POS Shifts Transactions" — one row per completed sale (not per shift), the finest-grained sales report. */
public record ShiftTransactionRow(
        Long orderId,
        Long shiftId,
        String registerName,
        String cashierName,
        String customerName,
        String invoiceNumber,
        Instant completedAt,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String paymentMethods
) {
}
