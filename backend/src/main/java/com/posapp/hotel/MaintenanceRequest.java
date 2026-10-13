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

/** A repair or fault in a room. While it is open and "blocks room" is set, the room cannot be sold. */
@Getter
@Setter
@Entity
public class MaintenanceRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Room room;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private MaintenancePriority priority = MaintenancePriority.NORMAL;

    @Enumerated(EnumType.STRING)
    private MaintenanceStatus status = MaintenanceStatus.OPEN;

    private boolean blocksRoom = false;

    private String reportedBy;
    private Instant resolvedAt;

    @Column(columnDefinition = "TEXT")
    private String resolutionNote;
}
