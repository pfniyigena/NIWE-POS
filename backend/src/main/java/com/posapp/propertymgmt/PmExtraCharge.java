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

/** A meter reading or a one-off charge. It waits for the next rent invoice of the lease and is billed on it once. */
@Getter
@Setter
@Entity
@Table(name = "pm_extra_charge", indexes = {@Index(columnList = "lease_id"), @Index(columnList = "rent_charge_id")})
public class PmExtraCharge extends BaseEntity {

    @Column(nullable = false)
    private Long leaseId;

    /** The metered charge on the lease this reading belongs to. */
    private Long leaseChargeId;

    private Long chargeTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PmChargeSource kind;

    @Column(nullable = false)
    private LocalDate chargeDate;

    @Column(nullable = false, length = 300)
    private String description;

    /** Units used (metered). */
    @Column(precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(length = 20)
    private String unitLabel;

    @Column(precision = 18, scale = 4)
    private BigDecimal rate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(precision = 18, scale = 3)
    private BigDecimal readingFrom;

    @Column(precision = 18, scale = 3)
    private BigDecimal readingTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PmExtraStatus status = PmExtraStatus.PENDING;

    /** The rent charge (period) whose invoice took it. */
    private Long rentChargeId;

    @Column(length = 60)
    private String invoiceNumber;

    @Column(length = 300)
    private String note;

    @Column(length = 255)
    private String createdByName;
}
