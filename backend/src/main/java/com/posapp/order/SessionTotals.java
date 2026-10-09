package com.posapp.order;

import java.math.BigDecimal;

/** Sales-side totals for one register session (till close / Z-report); refunds are added in by RegisterService. */
public record SessionTotals(
        int saleCount,
        BigDecimal grossSales,
        BigDecimal taxCollected,
        BigDecimal cashIn,
        BigDecimal nonCashIn
) {
}
