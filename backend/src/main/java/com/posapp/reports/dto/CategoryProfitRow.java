package com.posapp.reports.dto;

import java.math.BigDecimal;

/** One row of "Total POS Categories Profit" — revenue (pre-tax) minus cost of goods, grouped by category. */
public record CategoryProfitRow(
        Long categoryId,
        String categoryName,
        int quantitySold,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        BigDecimal marginPercent
) {
}
