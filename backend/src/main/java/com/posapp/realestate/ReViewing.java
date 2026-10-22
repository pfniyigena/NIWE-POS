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

import java.time.Instant;

/** A visit to a property by a lead, with an agent. */
@Getter
@Setter
@Entity
@Table(name = "re_viewing", indexes = {@Index(columnList = "property_id"), @Index(columnList = "lead_id"), @Index(columnList = "start_at")})
public class ReViewing extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    private Long agentUserId;
    private String agentName;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ViewingStatus status = ViewingStatus.SCHEDULED;

    /** What the lead said after the visit. */
    @Column(length = 2000)
    private String feedback;

    private String createdByName;

    /** When the lead was e-mailed a reminder and when the agent was. Both are cleared when the viewing is moved. */
    private Instant clientRemindedAt;
    private Instant agentRemindedAt;
}
