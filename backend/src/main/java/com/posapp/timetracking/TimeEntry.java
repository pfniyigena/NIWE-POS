package com.posapp.timetracking;

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
import java.time.Instant;
import java.time.LocalDate;

/** Time one person worked on one project on one day. Stored in whole minutes. */
@Getter
@Setter
@Entity
@Table(indexes = {@Index(columnList = "user_id,work_date"), @Index(columnList = "project_id")})
public class TimeEntry extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String userName;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private int minutes;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean billable = true;

    /** The hourly rate that applies, worked out when the entry is saved or submitted. */
    @Column(nullable = false)
    private BigDecimal hourlyRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeEntryStatus status = TimeEntryStatus.DRAFT;

    /** MANUAL or TIMER. */
    @Column(nullable = false, columnDefinition = "varchar(255) default 'MANUAL' not null")
    private String origin = "MANUAL";

    private Instant submittedAt;

    private Long decidedById;
    private String decidedByName;
    private Instant decidedAt;

    @Column(length = 500)
    private String rejectReason;

    @Column(name = "invoice_id")
    private Long invoiceId;
}
