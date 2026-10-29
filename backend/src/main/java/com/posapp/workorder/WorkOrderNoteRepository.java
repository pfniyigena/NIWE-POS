package com.posapp.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderNoteRepository extends JpaRepository<WorkOrderNote, Long> {
    List<WorkOrderNote> findByWorkOrderIdOrderByCreatedAtDescIdDesc(Long workOrderId);

    List<WorkOrderNote> findByWorkOrderIdOrderByCreatedAtAscIdAsc(Long workOrderId);
}
