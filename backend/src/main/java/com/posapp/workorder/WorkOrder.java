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

/**
 * A job: for a customer (billed) or internal maintenance and repair (cost only). Hours come from Time Tracking through the project
 * in {@link #timeProjectId}; other modules are linked by id only (asset, invoice, manufacturing order).
 */
@Getter
@Setter
@Entity
public class WorkOrder extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderType type = WorkOrderType.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderStatus status = WorkOrderStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderPriority priority = WorkOrderPriority.NORMAL;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    /** What is being serviced when it is not a registered asset (a customer's laptop, a vehicle plate...). */
    @Column(length = 500)
    private String itemDescription;

    private Long customerId;
    private String customerName;

    private Long assetId;
    private String assetName;

    private Long packageId;
    private String packageName;

    /** Fixed price of the job; 0 = bill hours and parts. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal fixedPrice = BigDecimal.ZERO;

    /** Hourly rate for the hours worked (billed on a customer job; informational on an internal one). */
    @Column(precision = 18, scale = 2)
    private BigDecimal labourRate;

    private Long assignedToUserId;
    private String assignedToName;

    private LocalDate requestedDate;
    private LocalDate scheduledDate;
    private LocalDate dueDate;
    private Instant startedAt;
    private Instant completedAt;

    private Long timeProjectId;
    private Long invoiceId;
    private String invoiceNumber;

    private Long manufacturingOrderId;
    private String manufacturingOrderNumber;

    private Long branchId;

    /** Stock was taken and the cost posted when the job was completed. */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean partsPosted = false;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal partsCostPosted = BigDecimal.ZERO;

    @Column(length = 2000)
    private String completionNote;

    private String createdByName;
}
