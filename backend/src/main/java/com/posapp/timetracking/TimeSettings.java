package com.posapp.timetracking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One row: how time is approved, rounded and billed. */
@Getter
@Setter
@Entity
public class TimeSettings extends BaseEntity {

    /** When off, submitted time is approved straight away. */
    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean requireApproval = true;

    /** A stopped timer is rounded up to a multiple of this many minutes (0 = exact minutes). */
    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int timerRoundingMinutes = 0;

    /** Rate used when neither the project nor the person has one. */
    @Column(nullable = false)
    private BigDecimal defaultHourlyRate = BigDecimal.ZERO;

    /** The most one person can log on one day. */
    @Column(nullable = false, columnDefinition = "integer default 16 not null")
    private int maxHoursPerDay = 16;

    /** Working hours in a day, used to work out utilisation (Monday to Friday). */
    @Column(nullable = false)
    private BigDecimal dailyCapacityHours = BigDecimal.valueOf(8);

    /** The service product that billed time is invoiced under (created on first use when not chosen). */
    @Column(name = "billing_product_id")
    private Long billingProductId;

    /** A timer left running longer than this many hours is stopped by the system (0 = never). */
    @Column(nullable = false, columnDefinition = "integer default 12 not null")
    private int timerAutoStopHours = 12;

    /** Time dated on or before this day can no longer be added, changed or deleted. */
    @Column(name = "locked_through")
    private java.time.LocalDate lockedThrough;

    /** E-mail people whose timesheet for last week is not complete. */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean reminderEnabled = false;

    /** Day of the week (1 = Monday ... 7 = Sunday) the reminder goes out. */
    @Column(nullable = false, columnDefinition = "integer default 1 not null")
    private int reminderDay = 1;

    @Column(name = "reminder_last_sent_on")
    private java.time.LocalDate reminderLastSentOn;

    /** A project that reaches this share of its budget is flagged (0 = no flag). */
    @Column(nullable = false, columnDefinition = "integer default 80 not null")
    private int budgetWarnPercent = 80;
}
