package com.posapp.accounting;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One side of one {@link Account} movement within a {@link JournalEntry} — see that class's
 * javadoc. Exactly one of {@code debit}/{@code credit} is normally positive and the other zero
 * (never both, never neither) for any single line; a journal entry balances when the sum of every
 * line's {@code debit} across the whole entry equals the sum of every line's {@code credit}.
 *
 * <p>{@code costCenter} is an optional second dimension (see {@link CostCenter}'s own javadoc) —
 * null on the overwhelming majority of lines, including every one of {@link
 * AccountingService}'s own automatic postings except {@link AccountingService#postExpense} and
 * {@link AccountingService#postIncome}, which tag only their expense/revenue-side line (never the
 * cash/payable/receivable offset) when the {@code Expense}/{@code Income} record itself carries
 * one. A manual entry ({@link JournalEntryService#createManual}) may set one per line freely.
 */
@Getter
@Setter
@Entity
public class JournalEntryLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private JournalEntry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;

    private BigDecimal debit = BigDecimal.ZERO;

    private BigDecimal credit = BigDecimal.ZERO;

    /** Optional per-line memo (e.g. "COGS — SKU-1234") — the entry's own memo covers the whole transaction, this covers just this one line when that's not enough. */
    private String description;

    /** See class javadoc — null unless this line was explicitly tagged with a cost center. */
    @ManyToOne(fetch = FetchType.LAZY)
    private CostCenter costCenter;
}
