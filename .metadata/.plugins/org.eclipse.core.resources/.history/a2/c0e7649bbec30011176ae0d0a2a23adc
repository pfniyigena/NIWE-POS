package com.posapp.tracking.dto;

import java.math.BigDecimal;

/** One batch/lot line for the batch and expiry reports. Dates and day counts are text so blanks show as dashes, not zeros. */
public record BatchReportRow(
        Long productId,
        String sku,
        String productName,
        String warehouseName,
        String locationName,
        String batchNumber,
        String expiryDate,
        String daysToExpiry,
        int quantity,
        BigDecimal unitCost,
        BigDecimal stockValue,
        String status
) {
}
