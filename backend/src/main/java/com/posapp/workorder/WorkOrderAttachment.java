package com.posapp.workorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A photo or file on a job, kept in the database so a backup holds it. */
@Getter
@Setter
@Entity
public class WorkOrderAttachment extends BaseEntity {
    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;
    @Column(nullable = false)
    private String fileName;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;
    @Column(length = 300)
    private String caption;
    private String uploadedByName;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
