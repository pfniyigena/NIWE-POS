package com.posapp.realestate;

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

/** Someone who asked about buying or renting. Becomes a customer when a deal starts. */
@Getter
@Setter
@Entity
@Table(name = "re_lead", indexes = {@Index(columnList = "status"), @Index(columnList = "assigned_user_id")})
public class ReLead extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 40)
    private String phone;

    /** The phone number with only its digits, to spot the same person entered twice. */
    @Column(length = 40)
    private String phoneKey;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LeadInterest interest = LeadInterest.BUY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStatus status = LeadStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadSource source = LeadSource.OTHER;

    @Column(precision = 18, scale = 2)
    private BigDecimal budgetMin;

    @Column(precision = 18, scale = 2)
    private BigDecimal budgetMax;

    @Column(length = 120)
    private String preferredArea;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PropertyType preferredType;

    private Integer minBedrooms;

    @Column(length = 1000)
    private String requirements;

    /** The property that brought the lead in, if there is one. */
    private Long propertyId;

    @Column(name = "assigned_user_id")
    private Long assignedUserId;
    private String assignedName;

    private LocalDate nextFollowUp;

    private Instant lastContactAt;

    @Column(length = 300)
    private String lostReason;

    /** Set when the lead was turned into a customer. */
    private Long customerId;

    private String createdByName;
}
