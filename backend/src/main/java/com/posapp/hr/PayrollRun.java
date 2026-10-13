package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One month's payroll for everybody. */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"period_year", "period_month"}))
public class PayrollRun extends BaseEntity {

    @Column(nullable = false)
    private int periodYear;

    @Column(nullable = false)
    private int periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollStatus status = PayrollStatus.DRAFT;

    @Column(nullable = false)
    private BigDecimal totalGross = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal totalPaye = BigDecimal.ZERO;
    /** Employee statutory contributions (not PAYE). */
    @Column(nullable = false)
    private BigDecimal totalEmployeeContributions = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal totalOtherDeductions = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal totalNet = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal totalEmployerContributions = BigDecimal.ZERO;

    private String createdByName;
    private String approvedByName;
    private Instant approvedAt;
    private LocalDate paidDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollLine> lines = new ArrayList<>();
}
