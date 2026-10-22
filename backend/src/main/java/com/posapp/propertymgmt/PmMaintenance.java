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

/** A repair or upkeep job on one unit: reported, scheduled, done, and who pays for it. */
@Getter
@Setter
@Entity
@Table(name = "pm_maintenance", indexes = {@Index(columnList = "unit_id"), @Index(columnList = "tenant_id"), @Index(columnList = "status"), @Index(columnList = "due_date")})
public class PmMaintenance extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private Long unitId;

    /** The tenant who reported it, or whose unit it is. */
    private Long tenantId;

    /** The lease in force on the unit when the request was made. */
    private Long leaseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MaintenanceCategory category = MaintenanceCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MaintenancePriority priority = MaintenancePriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MaintenanceStatus status = MaintenanceStatus.OPEN;

    @Column(nullable = false)
    private LocalDate reportedDate;

    @Column(length = 200)
    private String reportedBy;

    private LocalDate scheduledDate;

    private LocalDate dueDate;

    private LocalDate startedDate;

    private LocalDate completedDate;

    @Column(length = 200)
    private String contractorName;

    @Column(length = 40)
    private String contractorPhone;

    @Column(precision = 18, scale = 2)
    private BigDecimal estimatedCost;

    @Column(precision = 18, scale = 2)
    private BigDecimal actualCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CostBearer costBearer = CostBearer.OWNER;

    /** What was done, or why the request was cancelled. */
    @Column(length = 2000)
    private String resolution;

    private Long workOrderId;

    @Column(length = 60)
    private String workOrderNumber;

    /** The invoice sent to the tenant when the tenant pays. */
    private Long invoiceId;

    @Column(length = 60)
    private String invoiceNumber;

    @Column(length = 255)
    private String createdByName;
}
