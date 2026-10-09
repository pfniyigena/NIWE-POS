package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the "Financial Transactions" report — every journal entry line across every account in a date range, flattened; see AccountingReportService.financialTransactions. */
public record FinancialTransactionDto(
        Instant date,
        String entryNumber,
        String memo,
        String accountCode,
        String accountName,
        BigDecimal debit,
        BigDecimal credit,
        String description
) {
}
