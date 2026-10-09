package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of "POS Shifts Profit" — revenue (pre-tax) minus cost of goods, per shift. */
public record ShiftProfitRow(
        Long shiftId,
        String registerName,
        String openedByName,
        Instant openedAt,
        Instant closedAt,
        int saleCount,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        BigDecimal marginPercent
) {
}
