package com.posapp.installment;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One row: reminder behaviour and the starting values offered on a new instalment plan. */
@Getter
@Setter
@Entity
public class InstallmentSettings extends BaseEntity {

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean remindersEnabled = true;

    /** An upcoming instalment is announced once, this many days before it falls due. */
    @Column(nullable = false)
    private int reminderDaysBefore = 3;

    /** An overdue instalment is chased again every this many days. */
    @Column(nullable = false)
    private int overdueReminderEveryDays = 7;

    /** New plans start with whole-unit amounts (the right choice for currencies without cents). */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean defaultWholeUnits = false;

    @Column(nullable = false)
    private BigDecimal defaultInterestRate = BigDecimal.ZERO;

    @Column(nullable = false)
    private int defaultGraceDays = 5;

    @Column(nullable = false)
    private BigDecimal defaultLateFeeFixed = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal defaultLateFeePercent = BigDecimal.ZERO;

    /** On an early payoff, this share (0-100) of the interest not yet earned on instalments not yet due is waived. */
    @Column(nullable = false, columnDefinition = "numeric(38,2) default 100 not null")
    private BigDecimal payoffRebatePercent = BigDecimal.valueOf(100);

    /** A plan whose oldest unpaid instalment is this many days overdue is marked defaulted by the nightly job; 0 = never automatically. */
    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int defaultAfterDays = 0;

    /** The business's own wording for the agreement paper; empty = the standard text. */
    @Column(columnDefinition = "TEXT")
    private String agreementTerms;
}
