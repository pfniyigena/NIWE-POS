package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A kind of charge a tenant can be billed besides rent: Service charge, Garbage, Water, Electricity... */
@Getter
@Setter
@Entity
@Table(name = "pm_charge_type")
public class PmChargeType extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PmChargeKind kind = PmChargeKind.FIXED;

    /** What a meter counts (m3, kWh); only for metered charges. */
    @Column(length = 20)
    private String unitLabel;

    /** The usual monthly amount (fixed) or rate per unit (metered), offered when the charge is put on a lease. */
    @Column(precision = 18, scale = 2)
    private BigDecimal defaultAmount;

    @Column(nullable = false)
    private boolean active = true;

    /** The service product its invoice lines are made under (made on first use). */
    private Long productId;
}
