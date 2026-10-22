package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** A date that matters on a matter: a hearing, a filing date, a limitation date. */
@Getter
@Setter
@Entity
public class LawMatterDeadline extends BaseEntity {

    @Column(nullable = false)
    private Long matterId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadlineType type = DeadlineType.OTHER;

    @Column(nullable = false)
    private LocalDate dueDate;

    private LocalTime dueTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadlineStatus status = DeadlineStatus.OPEN;

    private Long assignedToUserId;
    private String assignedToName;

    @Column(length = 1000)
    private String notes;

    private Instant completedAt;
    private String completedByName;

    /** How far the escalation e-mails have gone: 0 none, 1 first warning, 2 last days, 3 overdue. Reset when the date changes. */
    @Column(nullable = false)
    private int escalationStage = 0;

    private Instant escalatedAt;

    /** The client sees this deadline in the portal (when the matter's portal shows deadlines). */
    @Column(nullable = false)
    private boolean visibleToClient = false;
}
