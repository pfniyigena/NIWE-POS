package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A salary advance or loan given to an employee and taken back through payroll, month by month. */
@Getter
@Setter
@Entity
public class StaffLoan extends BaseEntity {

    private String loanNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType;

    @Column(nullable = false)
    private BigDecimal principal;

    /** The most taken from each payroll (less at the end, when less is left). */
    @Column(nullable = false)
    private BigDecimal installment;

    @Column(nullable = false)
    private LocalDate issueDate;

    /** The first payroll month that takes an installment. */
    @Column(nullable = false)
    private int firstYear;
    @Column(nullable = false)
    private int firstMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status = LoanStatus.ACTIVE;

    @Column(name = "branch_id")
    private Long branchId;

    private String note;

    /** When set, the payroll of this month takes the whole balance of the loan (a leaver's last pay). */
    private Integer settleYear;
    private Integer settleMonth;
    private String createdByName;
}
