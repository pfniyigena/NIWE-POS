package com.posapp.accounting;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A monthly spending budget for one year, for either an expense category (account) or a cost center. */
@Getter
@Setter
@Entity
public class ExpenseBudget extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;
    @ManyToOne(fetch = FetchType.LAZY)
    private CostCenter costCenter;
    @Column(name = "budget_year", nullable = false)
    private int budgetYear;
    @Column(nullable = false)
    private BigDecimal monthlyAmount;
    private String note;
}
