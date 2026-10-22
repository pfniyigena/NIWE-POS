package com.posapp.law;

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

/** A matter: one case or piece of work for a client (a customer). Other modules and people are linked by id. */
@Getter
@Setter
@Entity
public class LawMatter extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String number;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatterStatus status = MatterStatus.OPEN;

    private String practiceArea;
    private String jurisdiction;

    /** Court, tribunal or other forum. */
    private String courtOrForum;

    /** The court's own case number. */
    @Column(length = 100)
    private String courtCaseNumber;

    private Long branchId;

    @Column(nullable = false)
    private Long clientCustomerId;

    @Column(nullable = false)
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingType billingType = BillingType.HOURLY;

    /** The fee agreement in words (rate, fixed fee, retainer amount, contingency percentage). */
    @Column(length = 500)
    private String billingNote;

    private LocalDate openedDate;
    private LocalDate closedDate;

    @Column(length = 1000)
    private String outcome;

    /** Only the matter's team and admins can see it (an ethical wall). */
    @Column(nullable = false)
    private boolean restricted = false;

    @Column(nullable = false)
    private boolean conflictCleared = false;

    @Column(length = 1000)
    private String conflictNote;

    private String conflictClearedBy;
    private Instant conflictClearedAt;

    private Long leadUserId;
    private String leadName;

    /** The Time Tracking project hours on this matter are logged to (created on first use). */
    private Long timeProjectId;

    /** Default hourly rate for time on this matter; empty falls back to the Time Tracking default. */
    @Column(precision = 18, scale = 2)
    private BigDecimal hourlyRate;

    /** The agreed fixed fee (billed in stages). */
    @Column(precision = 18, scale = 2)
    private BigDecimal fixedFee;

    /** The usual retainer to ask for. */
    @Column(precision = 18, scale = 2)
    private BigDecimal retainerAmount;

    /** The firm's share of what is recovered, in percent. */
    @Column(precision = 5, scale = 2)
    private BigDecimal contingencyPercent;

    private String createdByName;
}
