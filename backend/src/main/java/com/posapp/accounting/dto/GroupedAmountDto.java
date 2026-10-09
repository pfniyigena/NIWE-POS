package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One row of a grouped Expenses/Incomes report — see {@code AccountingReportService.expensesGrouped}/
 * {@code incomesGrouped}. {@code label} is whatever the {@code groupBy} parameter grouped on: an
 * account's "code — name" for {@code category}, a payee name for {@code vendor}/{@code staff}/
 * {@code client}, or an ISO date/week/month/year string for {@code daily}/{@code weekly}/{@code
 * monthly}/{@code yearly} — one backend method (per direction) backs all eight of that
 * direction's segmented+periodic report menu items, differing only in this parameter. {@code
 * details} is every individual Expense/Income entry that rolled into this row (Fourier: "Add all
 * details") — see ReportDetailLineDto's own javadoc.
 */
public record GroupedAmountDto(String label, BigDecimal amount, long count, List<ReportDetailLineDto> details) {
}
