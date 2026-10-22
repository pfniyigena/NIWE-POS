package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The late fee charged on one overdue rent charge (at most one per charge), invoiced to the tenant. */
@Getter
@Setter
@Entity
@Table(name = "pm_late_fee", uniqueConstraints = @UniqueConstraint(columnNames = {"rent_charge_id"}))
public class PmLateFee extends BaseEntity {

    @Column(nullable = false)
    private Long rentChargeId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate chargedOn;

    /** True when the morning job charged it, false when someone did. */
    @Column(nullable = false)
    private boolean auto;

    private Long invoiceId;

    @Column(length = 60)
    private String invoiceNumber;

    @Column(length = 120)
    private String chargedBy;
}
