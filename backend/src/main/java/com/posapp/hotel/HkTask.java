package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** A room to be cleaned on a day. */
@Getter
@Setter
@Entity
public class HkTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Room room;

    private LocalDate taskDate;

    @Enumerated(EnumType.STRING)
    private HkTaskKind kind;

    @Enumerated(EnumType.STRING)
    private HkTaskStatus status = HkTaskStatus.OPEN;

    @ManyToOne(fetch = FetchType.EAGER)
    private HkStaff staff;

    @Column(columnDefinition = "text")
    private String notes;

    private Instant startedAt;
    private Instant finishedAt;
    private Instant inspectedAt;
    private String inspectionResult;

    @Column(columnDefinition = "text")
    private String inspectionNote;

    private int attempts = 1;

    public Long minutes() {
        if (startedAt == null || finishedAt == null) return null;
        return Math.max(0, java.time.Duration.between(startedAt, finishedAt).toMinutes());
    }
}
