package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The rent for one payment period of a signed lease. It becomes an invoice to the tenant (as a customer) when it is billed. */
@Getter
@Setter
@Entity
@Table(name = "pm_rent_charge", uniqueConstraints = @UniqueConstraint(columnNames = {"lease_id", "period_start"}),
        indexes = {@Index(columnList = "lease_id"), @Index(columnList = "status"), @Index(columnList = "due_date"), @Index(columnList = "invoice_id")})
public class PmRentCharge extends BaseEntity {

    @Column(nullable = false)
    private Long leaseId;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** True when the period is shorter than a full one (the lease ended, or was cut short) and the rent is worked out by the day. */
    @Column(nullable = false)
    private boolean prorated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RentChargeStatus status = RentChargeStatus.PENDING;

    private Long invoiceId;

    @Column(length = 60)
    private String invoiceNumber;

    /** Remembered once the invoice has been seen fully paid, so lists need not ask the invoice again. The lease's own rent page always checks afresh. */
    @Column(nullable = false)
    private boolean settled;

    /** What the invoice took on top of the rent (utilities, service charges): the invoice total is the rent plus this. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal extras = BigDecimal.ZERO;

    @Column(length = 500)
    private String waivedReason;
}
