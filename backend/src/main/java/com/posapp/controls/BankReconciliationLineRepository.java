package com.posapp.controls;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BankReconciliationLineRepository extends JpaRepository<BankReconciliationLine, Long> {
    List<BankReconciliationLine> findByReconciliationId(Long reconciliationId);

    long countByReconciliationId(Long reconciliationId);

    List<BankReconciliationLine> findByJournalLineIdIn(Collection<Long> journalLineIds);

    @Modifying
    @Query("delete from BankReconciliationLine l where l.reconciliationId = :id")
    void deleteAllFor(@Param("id") Long id);

    @Modifying
    @Query("delete from BankReconciliationLine l where l.reconciliationId = :id and l.journalLineId in :ids")
    void deleteSome(@Param("id") Long id, @Param("ids") Collection<Long> ids);
}
