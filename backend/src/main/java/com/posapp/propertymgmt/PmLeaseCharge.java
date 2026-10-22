package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A charge a lease carries: a fixed amount per month, or a meter with a rate per unit. */
@Getter
@Setter
@Entity
@Table(name = "pm_lease_charge", indexes = {@Index(columnList = "lease_id")})
public class PmLeaseCharge extends BaseEntity {

    @Column(nullable = false)
    private Long leaseId;

    @Column(nullable = false)
    private Long chargeTypeId;

    /** Per month (fixed) or per unit used (metered). */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    /** Billed on rent periods that start on or after this day... */
    @Column(nullable = false)
    private LocalDate startDate;

    /** ...and on or before this one (empty: until the lease ends). */
    private LocalDate endDate;

    /** The meter showing when the charge was put on the lease (metered only). */
    @Column(precision = 18, scale = 3)
    private BigDecimal openingReading;

    @Column(length = 300)
    private String note;
}
