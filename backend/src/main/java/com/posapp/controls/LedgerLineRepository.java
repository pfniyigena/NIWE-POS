package com.posapp.controls;

import com.posapp.accounting.JournalEntryLine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Read-only views of ledger lines for reconciliation. */
public interface LedgerLineRepository extends JpaRepository<JournalEntryLine, Long> {

    /** Lines of one account dated before {@code before} that no other reconciliation has taken (this one's own ticked lines are included). */
    @Query("select l from JournalEntryLine l join fetch l.entry e where l.account.id = :account and e.entryDate < :before and e.entryDate >= :from "
            + "and l.id not in (select r.journalLineId from BankReconciliationLine r where r.reconciliationId <> :rec) order by e.entryDate asc, l.id asc")
    List<JournalEntryLine> candidates(@Param("account") Long account, @Param("from") Instant from, @Param("before") Instant before, @Param("rec") Long rec, Pageable page);

    @Query("select l from JournalEntryLine l join fetch l.entry e where l.id in :ids")
    List<JournalEntryLine> withEntries(@Param("ids") Collection<Long> ids);

    /** Net (debit - credit) of everything matched by finished reconciliations of this account that come before the given one (earlier statement, or same date and earlier). */
    @Query("select coalesce(sum(l.debit - l.credit), 0) from JournalEntryLine l where l.id in (select r.journalLineId from BankReconciliationLine r "
            + "where r.reconciliationId in (select b.id from BankReconciliation b where b.accountId = :account and b.status = 'DONE' "
            + "and (b.statementDate < :date or (b.statementDate = :date and b.id < :rec))))")
    java.math.BigDecimal clearedBefore(@Param("account") Long account, @Param("date") java.time.LocalDate date, @Param("rec") Long rec);

    @Query("select coalesce(sum(l.debit), 0) from JournalEntryLine l where l.id in (select r.journalLineId from BankReconciliationLine r where r.reconciliationId = :rec)")
    java.math.BigDecimal tickedDebit(@Param("rec") Long rec);

    @Query("select coalesce(sum(l.credit), 0) from JournalEntryLine l where l.id in (select r.journalLineId from BankReconciliationLine r where r.reconciliationId = :rec)")
    java.math.BigDecimal tickedCredit(@Param("rec") Long rec);

    @Query("select l.id from JournalEntryLine l where l.id in (select r.journalLineId from BankReconciliationLine r where r.reconciliationId = :rec)")
    List<Long> tickedIds(@Param("rec") Long rec);
}
