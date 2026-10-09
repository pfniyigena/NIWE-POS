package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * One row of "Total POS Brands Sales" (Fourier: "Add also report related to brand") — every
 * completed sale's lines, grouped by the product's brand at sale time. Mirrors
 * {@link CategorySalesRow} exactly, one level over: a product with no brand assigned groups under
 * "No Brand" the same way an uncategorized product groups under "Uncategorized".
 */
public record BrandSalesRow(
        Long brandId,
        String brandName,
        int quantitySold,
        BigDecimal salesTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal
) {
}
