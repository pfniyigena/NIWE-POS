package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A tenant's right to use a unit for a period at an agreed rent. */
@Getter
@Setter
@Entity
@Table(name = "pm_lease", indexes = {@Index(columnList = "unit_id"), @Index(columnList = "tenant_id"), @Index(columnList = "status"), @Index(columnList = "end_date")})
public class PmLease extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private Long unitId;

    @Column(nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private LeaseStatus status = LeaseStatus.DRAFT;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /** The rent for one payment period (see {@link #frequency}). */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal rent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RentFrequency frequency = RentFrequency.MONTHLY;

    /** Day of the month the rent falls due (1 to 28). */
    @Column(nullable = false)
    private int dueDay = 1;

    @Column(precision = 18, scale = 2)
    private BigDecimal deposit;

    /** The yearly rent increase agreed, as a percentage; used to suggest the rent when the lease is renewed. */
    @Column(precision = 7, scale = 2)
    private BigDecimal escalationPercent;

    /** How many days' notice either side gives to end the lease. */
    @Column(nullable = false)
    private int noticeDays = 30;

    private LocalDate signedDate;

    /** The lease this one renews. */
    private Long renewedFromId;

    private LocalDate terminatedDate;

    @Column(length = 1000)
    private String terminationReason;

    @Column(length = 2000)
    private String terms;

    @Column(length = 255)
    private String createdByName;
}
