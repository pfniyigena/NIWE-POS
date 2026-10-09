package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the General Ledger view for a single account — see AccountingReportService.ledger. */
public record LedgerLineDto(
        Instant entryDate,
        Long journalEntryId,
        String entryNumber,
        String memo,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        /** This account's own running balance immediately after this line, on its normal (increasing) side — see AccountType's javadoc. */
        BigDecimal runningBalance
) {
}
