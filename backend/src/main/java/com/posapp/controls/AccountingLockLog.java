package com.posapp.controls;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** One change of the closing date: who, when (createdAt), from what to what, and why. */
@Entity
@Table(name = "accounting_lock_log")
public class AccountingLockLog extends BaseEntity {

    @Column(name = "previous_date")
    private LocalDate previousDate;

    @Column(name = "new_date")
    private LocalDate newDate;

    @Column(name = "changed_by")
    private String changedBy;

    private String reason;

    public LocalDate getPreviousDate() { return this.previousDate; }
    public void setPreviousDate(LocalDate previousDate) { this.previousDate = previousDate; }
    public LocalDate getNewDate() { return this.newDate; }
    public void setNewDate(LocalDate newDate) { this.newDate = newDate; }
    public String getChangedBy() { return this.changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public String getReason() { return this.reason; }
    public void setReason(String reason) { this.reason = reason; }
}
