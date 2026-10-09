package com.posapp.accounting.dto;

import com.posapp.accounting.AccountType;

import java.math.BigDecimal;
import java.util.List;

/**
 * One account's own section of the General Ledger (Fourier: "General ledger ... add them") — the
 * traditional "every account, back to back" ledger book, as opposed to {@code
 * AccountingReportService#ledger}'s single-account drill-down reached from Chart of Accounts.
 * {@code lines} reuses {@link LedgerLineDto} exactly as that single-account view does (each line's
 * own {@code runningBalance} already carries forward from {@code openingBalance}), so this is
 * really just "every account's own ledger, assembled into one report" rather than new math.
 */
public record GeneralLedgerAccountDto(
        Long accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        List<LedgerLineDto> lines
) {
}
