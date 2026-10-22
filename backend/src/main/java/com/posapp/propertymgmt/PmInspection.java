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

import java.time.LocalDate;

/** A walk-through of a unit with the tenant: room by room and item by item, what condition it is in. */
@Getter
@Setter
@Entity
@Table(name = "pm_inspection", indexes = {@Index(columnList = "lease_id")})
public class PmInspection extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "lease_id", nullable = false)
    private Long leaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PmInspectionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PmInspectionStatus status = PmInspectionStatus.DRAFT;

    @Column(nullable = false)
    private LocalDate inspectionDate;

    @Column(length = 200)
    private String inspectorName;

    @Column(length = 40)
    private String electricityReading;

    @Column(length = 40)
    private String waterReading;

    private Integer keysHanded;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private boolean tenantAcknowledged = false;

    @Column(length = 200)
    private String tenantSignedName;

    private LocalDate signedOffOn;

    private String signedOffByName;

    private String createdByName;
}
