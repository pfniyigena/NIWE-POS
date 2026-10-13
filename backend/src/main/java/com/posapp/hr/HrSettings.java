package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/** One row: the working-time and payroll settings shared by the HR modules. */
@Getter
@Setter
@Entity
public class HrSettings extends BaseEntity {

    /** Normal start of the working day; clocking in later than this plus the grace period counts as late. */
    @Column(nullable = false)
    private LocalTime workStart = LocalTime.of(8, 0);

    @Column(nullable = false)
    private int graceMinutes = 15;

    /** Hours in a normal working day; hours beyond this are overtime. */
    @Column(nullable = false)
    private BigDecimal standardDailyHours = new BigDecimal("8");

    /** Comma-separated: MON,TUE,WED,THU,FRI */
    @Column(nullable = false)
    private String workingDays = "MON,TUE,WED,THU,FRI";

    @Column(nullable = false)
    private BigDecimal overtimeMultiplier = new BigDecimal("1.5");

    /** True when the income-tax bands are yearly amounts (the monthly pay is annualised, taxed, and divided by 12). */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean taxBandsAnnual = false;

    /** The ready-made leave types have been created once (so deleting them later doesn't bring them back). */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean leaveDefaultsLoaded = false;

    /** The ready-made payroll rules and tax bands for the store's country have been created once. */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean payrollDefaultsLoaded = false;
}
