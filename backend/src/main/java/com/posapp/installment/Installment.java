package com.posapp.installment;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One dated instalment of a plan. What has been paid is not stored here: it is worked out oldest-first from the plan's payments. */
@Getter
@Setter
@Entity
@Table(name = "installment", uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "seq"}))
public class Installment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private InstallmentPlan plan;

    /** 1, 2, 3 … in due-date order. */
    @Column(nullable = false)
    private int seq;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private BigDecimal principalDue = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal interestDue = BigDecimal.ZERO;

    /** Added once when the instalment went past its grace period unpaid (0 until then, or if waived). */
    @Column(nullable = false)
    private BigDecimal lateFee = BigDecimal.ZERO;
    private LocalDate lateFeeAssessedOn;
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean lateFeeWaived = false;

    private LocalDate reminderSentOn;
    private LocalDate lastOverdueReminderOn;
}
