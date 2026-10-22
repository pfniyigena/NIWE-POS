package com.posapp.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkOrderLineRepository extends JpaRepository<WorkOrderLine, Long> {
    List<WorkOrderLine> findByWorkOrderIdOrderByIdAsc(Long workOrderId);

    List<WorkOrderLine> findByWorkOrderIdIn(Collection<Long> workOrderIds);
}
