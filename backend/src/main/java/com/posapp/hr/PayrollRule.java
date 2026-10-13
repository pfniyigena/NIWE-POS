package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A statutory contribution: a percentage of pay withheld from the employee or paid on top by the employer. */
@Getter
@Setter
@Entity
public class PayrollRule extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleBase base = RuleBase.GROSS;

    @Column(nullable = false)
    private BigDecimal ratePercent;

    /** The most a month's base can count for (null = no cap). */
    private BigDecimal capAmount;

    /** Employee contributions only: taken off before income tax is worked out. */
    private boolean deductibleFromTax = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayableTarget target = PayableTarget.SOCIAL_SECURITY;

    private boolean active = true;

    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int sortOrder = 0;
}
