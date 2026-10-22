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
}
