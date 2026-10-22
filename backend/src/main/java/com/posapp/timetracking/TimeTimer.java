package com.posapp.timetracking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A running stopwatch. A person has at most one. */
@Getter
@Setter
@Entity
public class TimeTimer extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean billable = true;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();
}
