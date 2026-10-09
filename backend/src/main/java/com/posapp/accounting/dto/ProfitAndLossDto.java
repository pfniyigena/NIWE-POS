package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Admin > Accounting > Reports > Profit & Loss, for a date range — see AccountingReportService.profitAndLoss. */
public record ProfitAndLossDto(
        Instant from,
        Instant to,
        List<AccountAmountDto> revenue,
        List<AccountAmountDto> expenses,
        BigDecimal totalRevenue,
        BigDecimal totalExpenses,
        BigDecimal netIncome
) {
}
