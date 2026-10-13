package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** One employee's pay for the month; a snapshot, so later changes to the employee or contract don't rewrite it. */
@Getter
@Setter
@Entity
public class PayrollLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PayrollRun run;

    private Long employeeId;
    private String employeeNumber;
    private String employeeName;
    private String departmentName;
    private String bankName;
    private String bankAccount;

    @Column(name = "branch_id")
    private Long branchId;

    private Long contractId;
    private String contractNumber;

    @Column(nullable = false)
    private BigDecimal baseSalary = BigDecimal.ZERO;
    private int daysInMonth;
    private int payableDays;
    private BigDecimal unpaidLeaveDays = BigDecimal.ZERO;
    private BigDecimal absentDays = BigDecimal.ZERO;
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal grossPay = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal taxablePay = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal payeTax = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal employeeContributions = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal otherDeductions = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal netPay = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal employerContributions = BigDecimal.ZERO;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollItem> items = new ArrayList<>();
}
