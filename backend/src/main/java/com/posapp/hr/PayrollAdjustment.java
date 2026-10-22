package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A one-off amount added to or taken from one employee's pay for one month. */
@Getter
@Setter
@Entity
public class PayrollAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdjustmentKind kind;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;

    /** Earnings only: whether the amount counts as taxable pay. */
    private boolean taxable = true;

    @Column(nullable = false)
    private int periodYear;

    @Column(nullable = false)
    private int periodMonth;

    private String note;

    /** FINAL_PAY when made by the final-pay screen; empty when entered by hand. */
    @Column(length = 20)
    private String source;
    private String createdByName;
}
