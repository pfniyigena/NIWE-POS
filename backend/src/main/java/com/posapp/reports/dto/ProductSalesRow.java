package com.posapp.reports.dto;

import java.math.BigDecimal;

/** One row of "Total POS Products Sales" — every completed sale's lines, grouped by product. */
public record ProductSalesRow(
        Long productId,
        String sku,
        String productName,
        String categoryName,
        int quantitySold,
        BigDecimal salesTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal
) {
}
