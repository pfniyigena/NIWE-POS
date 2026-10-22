package com.posapp.controls;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A ledger line ticked as "it is on the statement". A ledger line can be in at most one reconciliation. */
@Entity
@Table(name = "bank_reconciliation_line")
public class BankReconciliationLine extends BaseEntity {

    @Column(name = "reconciliation_id", nullable = false)
    private Long reconciliationId;

    @Column(name = "journal_line_id", nullable = false)
    private Long journalLineId;

    public Long getReconciliationId() { return this.reconciliationId; }
    public void setReconciliationId(Long reconciliationId) { this.reconciliationId = reconciliationId; }
    public Long getJournalLineId() { return this.journalLineId; }
    public void setJournalLineId(Long journalLineId) { this.journalLineId = journalLineId; }
}
