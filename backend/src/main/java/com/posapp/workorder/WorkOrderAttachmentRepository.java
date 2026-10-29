package com.posapp.workorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkOrderAttachmentRepository extends JpaRepository<WorkOrderAttachment, Long> {
    /** The list never loads the file bytes. */
    interface Row {
        Long getId();

        String getFileName();

        String getContentType();

        long getSizeBytes();

        String getCaption();

        String getUploadedByName();

        java.time.Instant getCreatedAt();
    }

    @Query("select a.id as id, a.fileName as fileName, a.contentType as contentType, a.sizeBytes as sizeBytes, a.caption as caption, "
            + "a.uploadedByName as uploadedByName, a.createdAt as createdAt from WorkOrderAttachment a where a.workOrderId = :workOrderId order by a.createdAt asc, a.id asc")
    List<Row> rows(Long workOrderId);

    long countByWorkOrderId(Long workOrderId);
}
