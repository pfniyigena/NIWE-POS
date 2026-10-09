package com.posapp.accounting;

/**
 * The five fundamental account categories every {@link Account} falls into — standard double-entry
 * bookkeeping. Which side is an account's "normal" (increasing) balance side is fixed by its type,
 * not stored separately: {@code ASSET}/{@code EXPENSE} accounts normally carry a debit balance
 * (a debit increases them, a credit decreases them); {@code LIABILITY}/{@code EQUITY}/{@code
 * REVENUE} accounts normally carry a credit balance (the reverse). {@link
 * AccountingReportService} uses this to compute a running/period balance for the General Ledger,
 * Trial Balance, Profit &amp; Loss and Balance Sheet reports without needing a second field on
 * {@link Account} itself.
 */
public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}
