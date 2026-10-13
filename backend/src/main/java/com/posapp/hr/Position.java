package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A job position (title) in the organisation, optionally within a department. */
@Getter
@Setter
@Entity
@Table(name = "hr_position")
public class Position extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** How many people this position is planned for (0 = not set). */
    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int plannedHeadcount = 0;

    private boolean active = true;
}
