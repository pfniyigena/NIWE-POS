package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One line of a payslip: an earning (negative for unpaid days), a deduction, the income tax, or an employer cost. */
@Getter
@Setter
@Entity
public class PayrollItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PayrollLine line;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemKind kind;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;

    /** Earnings: counts as taxable pay. */
    private boolean taxable;

    /** Deductions and employer costs: which liability account it is owed in. Null for earnings and for the income tax (PAYE). */
    @Enumerated(EnumType.STRING)
    private PayableTarget target;

    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int sortOrder = 0;
}
