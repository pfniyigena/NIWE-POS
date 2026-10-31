package com.posapp.accounting;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One payment (full or part) made on an approved, unpaid expense bill. */
@Getter
@Setter
@Entity
public class ExpensePayment extends BaseEntity {
    @Column(name = "expense_id", nullable = false)
    private Long expenseId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private LocalDate paidOn;
    /** An ASSET account (bank, mobile money…); null = Cash. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Account paidFromAccount;
    private String note;
    private String createdByName;
}
