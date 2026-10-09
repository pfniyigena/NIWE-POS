package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Task 17: "Daily credit collection report" — one row per calendar day, summing every customer payment recorded that day. */
public record DailyCreditCollectionRow(
        LocalDate date,
        int paymentCount,
        BigDecimal totalCollected
) {
}
