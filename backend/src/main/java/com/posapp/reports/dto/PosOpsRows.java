package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Rows of the POS operations reports (by cashier, by hour, by payment method, exceptions, shift comparison). */
public final class PosOpsRows {

    private PosOpsRows() {
    }

    public record CashierRow(String cashierName, int shifts, int saleCount, int itemCount, BigDecimal grandTotal, BigDecimal discounts, int refundCount, BigDecimal refundTotal,
                             int voidCount, BigDecimal netSales, BigDecimal averageSale) {
    }

    public record HourRow(String hour, int saleCount, int itemCount, BigDecimal grandTotal, BigDecimal averageSale, BigDecimal sharePercent) {
    }

    public record PaymentRow(String method, int paymentCount, BigDecimal amount, int refundCount, BigDecimal refundAmount, BigDecimal netAmount, BigDecimal sharePercent) {
    }

    public record ExceptionRow(Instant at, String type, String reference, String customer, String registerName, String by, BigDecimal amount, String reason, String approvedBy) {
    }

    public record ShiftCompareRow(Long shiftId, Integer zNumber, String registerName, String cashierName, Instant openedAt, Instant closedAt, int saleCount, BigDecimal grandTotal,
                                  BigDecimal averageSale, BigDecimal itemsPerSale, BigDecimal discounts, BigDecimal refundTotal, int voidCount, BigDecimal expectedCash,
                                  BigDecimal countedCash, BigDecimal variance, BigDecimal salesVsAveragePercent) {
    }
}
