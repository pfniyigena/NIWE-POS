package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Admin > Accounting > Reports > Balance Sheet, as of one point in time — see
 * AccountingReportService.balanceSheet. {@code currentEarnings} is Revenue-minus-Expenses computed
 * the same way {@link ProfitAndLossDto} does, but all-time up to {@code asOf} rather than for a
 * chosen period — a live "retained earnings not yet closed to Owner's Equity" figure, exactly what
 * makes {@code totalAssets} equal {@code totalLiabilities + totalEquity + currentEarnings} by the
 * basic mechanics of double-entry bookkeeping (every revenue/expense line already moved some real
 * asset/liability account too) rather than needing an explicit period-close journal entry. {@code
 * balanced} is that equality, spelled out for the UI rather than trusting the reader to check it.
 */
public record BalanceSheetDto(
        Instant asOf,
        List<AccountAmountDto> assets,
        List<AccountAmountDto> liabilities,
        List<AccountAmountDto> equity,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity,
        BigDecimal currentEarnings,
        boolean balanced
) {
}
