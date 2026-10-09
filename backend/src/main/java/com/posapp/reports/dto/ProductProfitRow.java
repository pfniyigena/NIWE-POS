package com.posapp.reports.dto;

import java.math.BigDecimal;

/** One row of "Total POS Products Profit" — revenue (pre-tax) minus cost of goods, grouped by product. */
public record ProductProfitRow(
        Long productId,
        String sku,
        String productName,
        String categoryName,
        int quantitySold,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        BigDecimal marginPercent
) {
}
