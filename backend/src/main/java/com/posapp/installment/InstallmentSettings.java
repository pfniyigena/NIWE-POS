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
}
