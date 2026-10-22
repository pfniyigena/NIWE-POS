package com.posapp.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkOrderChecklistItemRepository extends JpaRepository<WorkOrderChecklistItem, Long> {
    List<WorkOrderChecklistItem> findByWorkOrderIdOrderBySeqAsc(Long workOrderId);

    List<WorkOrderChecklistItem> findByWorkOrderIdIn(Collection<Long> workOrderIds);
}
