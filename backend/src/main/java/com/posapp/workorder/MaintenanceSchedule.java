package com.posapp.workorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A recurring job (service the generator every 3 months, inspect the fire extinguishers every year) that makes the next work order on its own. */
@Getter
@Setter
@Entity
public class MaintenanceSchedule extends BaseEntity {
    @Column(nullable = false)
    private String title;
    @Column(length = 4000)
    private String description;
    @Column(length = 500)
    private String itemDescription;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderType type = WorkOrderType.INTERNAL;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderPriority priority = WorkOrderPriority.NORMAL;
    private Long customerId;
    private String customerName;
    private Long assetId;
    private String assetName;
    private Long packageId;
    private String packageName;
    private Long assignedToUserId;
    private String assignedToName;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal fixedPrice = BigDecimal.ZERO;
    @Column(precision = 18, scale = 2)
    private BigDecimal labourRate;
    private Long branchId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IntervalUnit intervalUnit = IntervalUnit.MONTHS;
    @Column(nullable = false)
    private int intervalCount = 1;
    /** The date the next job is meant to be done; the job is made {@link #leadDays} before it. */
    @Column(nullable = false)
    private LocalDate nextDueDate;
    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int leadDays = 0;
    /** Days after the scheduled date by which the job must be finished (0 = the due date is the scheduled date). */
    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int dueAfterDays = 0;
    /** Do not make a new job while the last one from this schedule is still open, in progress or on hold. */
    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean skipIfOpen = true;
    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean active = true;
    private Instant lastGeneratedAt;
    private Long lastWorkOrderId;
    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int timesGenerated = 0;
    private String createdByName;
}
