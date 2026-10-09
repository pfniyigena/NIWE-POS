package com.posapp.accounting.dto;

import com.posapp.accounting.AccountType;

import java.math.BigDecimal;
import java.util.List;

/** One account's totals for a Trial Balance report — see AccountingReportService.trialBalance. */
public record TrialBalanceRowDto(
        Long accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        /** totalDebit - totalCredit for a debit-normal account (ASSET/EXPENSE), or the reverse for a credit-normal one — the single figure a trial balance actually reports per account. */
        BigDecimal balance,
        /** Every journal-entry line that rolled into this account's totals above, in date order (Fourier: "Add all details") — see ReportDetailLineDto's own javadoc. */
        List<ReportDetailLineDto> details
) {
}
