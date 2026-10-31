package com.posapp.accounting;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** One line of an expense's history. Never edited or deleted. */
@Getter
@Setter
@Entity
public class ExpenseEvent extends BaseEntity {
    @Column(name = "expense_id", nullable = false)
    private Long expenseId;
    @Column(nullable = false, length = 30)
    private String kind;
    @Column(length = 1000)
    private String detail;
    private String createdByName;
}
