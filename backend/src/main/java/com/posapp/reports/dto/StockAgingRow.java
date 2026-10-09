package com.posapp.reports.dto;

import java.math.BigDecimal;

/**
 * Stock aging / dead-stock row: one product with sellable stock and how long since it last sold.
 * Dates are ISO yyyy-MM-dd strings (null = never); {@code daysSinceLastSale} is text ("Never" when it
 * has not sold) so the generic report table can show it without a misleading zero.
 */
public record StockAgingRow(
        Long productId,
        String sku,
        String productName,
        String categoryName,
        int quantity,
        BigDecimal unitCost,
        BigDecimal stockValue,
        String lastReceived,
        String lastSold,
        String daysSinceLastSale,
        String ageBucket
) {
}
