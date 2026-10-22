package com.posapp.controls;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One statement being matched against the ledger account it belongs to. DRAFT while lines are being ticked, DONE once the difference is zero. */
@Entity
@Table(name = "bank_reconciliation")
public class BankReconciliation extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "statement_balance", nullable = false)
    private BigDecimal statementBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = "DRAFT";

    private String note;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getAccountId() { return this.accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public LocalDate getStatementDate() { return this.statementDate; }
    public void setStatementDate(LocalDate statementDate) { this.statementDate = statementDate; }
    public BigDecimal getStatementBalance() { return this.statementBalance; }
    public void setStatementBalance(BigDecimal statementBalance) { this.statementBalance = statementBalance; }
    public String getStatus() { return this.status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return this.note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return this.createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCompletedBy() { return this.completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public Instant getCompletedAt() { return this.completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
