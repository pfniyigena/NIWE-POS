package com.posapp.workorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A dated note on a job (what was found, what was agreed with the customer). */
@Getter
@Setter
@Entity
public class WorkOrderNote extends BaseEntity {
    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;
    private Long authorUserId;
    private String authorName;
    @Column(nullable = false, length = 2000)
    private String text;
}
