package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * One row of "Total POS Brands Profit" (Fourier: "Add also report related to brand") — revenue
 * (pre-tax) minus cost of goods, grouped by brand. Mirrors {@link CategoryProfitRow} exactly, one
 * level over.
 */
public record BrandProfitRow(
        Long brandId,
        String brandName,
        int quantitySold,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        BigDecimal marginPercent
) {
}
