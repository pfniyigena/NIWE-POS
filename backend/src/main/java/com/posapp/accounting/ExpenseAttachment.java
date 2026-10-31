package com.posapp.accounting;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A receipt, bill or other file kept with an expense. */
@Getter
@Setter
@Entity
public class ExpenseAttachment extends BaseEntity {
    @Column(name = "expense_id", nullable = false)
    private Long expenseId;
    @Column(nullable = false)
    private String fileName;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;
    private String uploadedByName;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
