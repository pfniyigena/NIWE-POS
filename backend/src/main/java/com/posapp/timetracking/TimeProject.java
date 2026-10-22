package com.posapp.timetracking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What time is logged against and billed from. A project belongs to a customer. Other modules (work orders, law-firm matters)
 * create or link a project through {@link #sourceType} and {@link #sourceId} instead of having a time system of their own.
 */
@Getter
@Setter
@Entity
public class TimeProject extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String code;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    /** New time on this project is billable unless the person unticks it. */
    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean billable = true;

    /** The project's hourly rate; empty falls back to the default rate in the settings. */
    private BigDecimal defaultRate;

    private BigDecimal budgetHours;

    private LocalDate startDate;
    private LocalDate endDate;

    /** MANUAL, or the module that created the project (WORK_ORDER, MATTER). */
    @Column(nullable = false, columnDefinition = "varchar(255) default 'MANUAL' not null")
    private String sourceType = "MANUAL";

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "branch_id")
    private Long branchId;
}
