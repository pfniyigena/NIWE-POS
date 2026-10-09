package com.posapp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findAllByOrderByEntryDateDesc();

    List<JournalEntry> findByEntryDateBetweenOrderByEntryDateAsc(Instant from, Instant to);

    /** One account's own lines across every entry in range, oldest first — the General Ledger view. Joins through JournalEntryLine.entry/account rather than a separate repository, since a line is never queried on its own outside this. */
    @Query("select l from JournalEntryLine l where l.account.id = :accountId and l.entry.entryDate between :from and :to order by l.entry.entryDate asc, l.id asc")
    List<JournalEntryLine> findLinesForAccountInRange(@Param("accountId") Long accountId, @Param("from") Instant from, @Param("to") Instant to);

    /** Every line for one account up to (and including) a point in time, regardless of lower bound — what the Balance Sheet's as-of-date balance needs. */
    @Query("select l from JournalEntryLine l where l.account.id = :accountId and l.entry.entryDate <= :asOf")
    List<JournalEntryLine> findLinesForAccountUpTo(@Param("accountId") Long accountId, @Param("asOf") Instant asOf);

    /** Every line in range, across every account — the Trial Balance / Profit & Loss reports' raw input. */
    @Query("select l from JournalEntryLine l where l.entry.entryDate between :from and :to")
    List<JournalEntryLine> findAllLinesInRange(@Param("from") Instant from, @Param("to") Instant to);

    /** Every line up to (and including) a point in time — the Balance Sheet's raw input. */
    @Query("select l from JournalEntryLine l where l.entry.entryDate <= :asOf")
    List<JournalEntryLine> findAllLinesUpTo(@Param("asOf") Instant asOf);

    /** Every line tagged with any cost center, in range — the "Cost Centers" journals report's raw input (see AccountingReportService.costCenterActivity). */
    @Query("select l from JournalEntryLine l where l.costCenter is not null and l.entry.entryDate between :from and :to")
    List<JournalEntryLine> findAllLinesWithCostCenterInRange(@Param("from") Instant from, @Param("to") Instant to);
}
