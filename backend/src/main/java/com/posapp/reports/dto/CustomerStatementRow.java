package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the Customer Statement (Task: "Add Customers Report (Customer Statement)") — one
 * row per completed sale that one specific customer made in the chosen date range. No
 * customer/shift id here the way {@code ShiftTransactionRow} has — the whole list is already
 * scoped to one customer (see {@code ReportController.customerStatement}), so repeating their
 * name on every row would be redundant; the screen shows it once, in the page header instead.
 *
 * <p>{@code creditAmount}/{@code dueDate}/{@code amountOutstanding} are additive columns (Task 9
 * implicitly touches store credit once it exists) — null/zero for a sale with no credit portion.
 * Deliberately kept as columns on the existing invoice-per-row shape rather than restructuring
 * this into a mixed ledger of invoices and payments interleaved chronologically — that
 * interpretation is served by the separate Payment History screen instead, so this report's
 * existing shape (and every screen already built on it) never breaks.
 */
public record CustomerStatementRow(
        Long orderId,
        String invoiceNumber,
        Instant completedAt,
        String registerName,
        String cashierName,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String paymentMethods,
        BigDecimal creditAmount,
        LocalDate dueDate,
        BigDecimal amountOutstanding
) {
}
