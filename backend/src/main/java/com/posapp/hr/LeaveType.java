package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class LeaveType extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    /** Working days a year an employee may take (0 = no limit, e.g. unpaid leave). */
    @Column(nullable = false)
    private int daysPerYear = 0;

    /** Unpaid leave days are deducted from pay by payroll. */
    private boolean paid = true;

    private boolean active = true;

    /** UPFRONT: the whole yearly entitlement is available from January. MONTHLY: a twelfth is earned for every month worked in the year. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveAccrual accrualMode = LeaveAccrual.UPFRONT;

    /** The most unused days of the previous year that carry over into the next one (0 = none carry over). */
    @Column(nullable = false)
    private int carryOverMaxDays = 0;

    /** Unused days of this type are paid out in the final pay when an employee leaves. */
    @Column(nullable = false)
    private boolean payoutOnExit = false;
}
