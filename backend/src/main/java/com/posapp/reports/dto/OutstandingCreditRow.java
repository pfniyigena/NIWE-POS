package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * Task 10: "Outstanding credit report" — snapshot-style, no date range: one row per customer who
 * currently owes anything at all, regardless of when those sales happened or whether credit is
 * still enabled for them today.
 */
public record OutstandingCreditRow(
        Long customerId,
        String customerName,
        String phone,
        BigDecimal creditLimit,
        BigDecimal outstandingBalance,
        BigDecimal availableCredit
) {
}
