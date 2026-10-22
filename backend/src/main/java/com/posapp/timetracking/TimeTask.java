package com.posapp.timetracking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A named piece of work inside a project (optional on an entry). */
@Getter
@Setter
@Entity
public class TimeTask extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String name;

    private BigDecimal estimatedHours;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean active = true;
}
