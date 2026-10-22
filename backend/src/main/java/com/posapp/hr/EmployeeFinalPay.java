package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What was worked out for an employee who left: unused leave paid out, notice paid or recovered, loans taken back. The amounts reach payroll as adjustments. */
@Getter
@Setter
@Entity
public class EmployeeFinalPay extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Employee employee;

    @Column(nullable = false)
    private int periodYear;

    @Column(nullable = false)
    private int periodMonth;

    @Column(nullable = false)
    private LocalDate lastDay;

    @Column(nullable = false)
    private BigDecimal dailyRate = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal leaveDays = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal leaveAmount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal noticePayDays = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal noticePayAmount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal noticeRecoverDays = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal noticeRecoverAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean settleLoans = false;

    @Column(length = 300)
    private String note;

    private String createdByName;
}
