package com.posapp.controls;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** The one row that says the books are closed up to (and including) a date. Null date = open. */
@Entity
@Table(name = "accounting_lock")
public class AccountingLock extends BaseEntity {

    @Column(name = "lock_date")
    private LocalDate lockDate;

    @Column(name = "updated_by")
    private String updatedBy;

    public LocalDate getLockDate() { return this.lockDate; }
    public void setLockDate(LocalDate lockDate) { this.lockDate = lockDate; }
    public String getUpdatedBy() { return this.updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
