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

/** One charge line taken by a rent invoice (kept so the screens and reports can show it without opening the invoice). */
@Getter
@Setter
@Entity
@Table(name = "pm_billed_charge", indexes = {@Index(columnList = "rent_charge_id"), @Index(columnList = "lease_id")})
public class PmBilledCharge extends BaseEntity {

    @Column(nullable = false)
    private Long rentChargeId;

    @Column(nullable = false)
    private Long leaseId;

    private Long chargeTypeId;

    /** The charge type's name when the line was made. */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 400)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PmChargeSource source;

    private Long extraId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
}
