package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
}
