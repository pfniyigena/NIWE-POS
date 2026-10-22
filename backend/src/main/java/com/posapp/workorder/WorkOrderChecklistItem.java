package com.posapp.workorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
public class WorkOrderChecklistItem extends BaseEntity {

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean done = false;

    private Instant doneAt;
    private String doneByName;
}
