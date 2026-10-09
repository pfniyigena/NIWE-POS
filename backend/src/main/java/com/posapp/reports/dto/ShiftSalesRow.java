package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of "POS Shifts Sales" — every shift (RegisterSession) with at least one completed sale in range, totalled. */
public record ShiftSalesRow(
        Long shiftId,
        String registerName,
        String openedByName,
        Instant openedAt,
        Instant closedAt,
        String status,
        int saleCount,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal
) {
}
