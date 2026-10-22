package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** Something that happened to a payroll after it was approved: payment cancelled, payroll reopened. */
@Getter
@Setter
@Entity
public class PayrollRunEvent extends BaseEntity {

    @Column(nullable = false)
    private Long runId;

    /** PAYMENT_CANCELLED or REOPENED. */
    @Column(nullable = false, length = 24)
    private String kind;

    @Column(length = 300)
    private String note;

    private String byName;
}
