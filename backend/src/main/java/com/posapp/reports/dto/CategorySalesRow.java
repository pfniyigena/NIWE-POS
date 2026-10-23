package com.posapp.reports.dto;

import java.math.BigDecimal;

/** One row of "Total POS Categories Sales" — every completed sale's lines, grouped by the product's category at sale time. */
public record CategorySalesRow(
        Long categoryId,
        String categoryName,
        BigDecimal quantitySold,
        BigDecimal salesTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal
) {
}
