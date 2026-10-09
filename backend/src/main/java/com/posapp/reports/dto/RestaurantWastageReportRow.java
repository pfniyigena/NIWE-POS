package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * "Restaurant Wastage" — every {@code RestaurantWastage} entry in the date range, any reason, any
 * {@code status} (Fourier: "should be confirmed by admin once confirmed inventory and account
 * will be impacted"), whether or not the product was stock-tracked (a made-to-order/service item
 * still logs the loss, it just has no stock to snapshot even once confirmed — see {@code
 * RestaurantWastage}'s own javadoc). {@code stockBefore}/{@code stockAfter}/{@code costImpact} are
 * all null for a PENDING or REJECTED entry — only a CONFIRMED one ever had a real impact.
 * {@code costImpact}, when set, is what {@code AccountingService.postRestaurantWastage} posted as
 * Inventory Shrinkage Expense for this one entry — summing this column (CONFIRMED rows only) is
 * the same number the P&L would show for restaurant wastage over the period.
 */
public record RestaurantWastageReportRow(
        Instant recordedAt,
        String sku,
        String productName,
        int quantity,
        String reason,
        String note,
        /** "PENDING" | "CONFIRMED" | "REJECTED". */
        String status,
        Integer stockBefore,
        Integer stockAfter,
        BigDecimal costImpact
) {
}
