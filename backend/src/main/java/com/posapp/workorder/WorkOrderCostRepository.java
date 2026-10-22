package com.posapp.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkOrderCostRepository extends JpaRepository<WorkOrderCost, Long> {
    List<WorkOrderCost> findByWorkOrderIdOrderByCostDateAscIdAsc(Long workOrderId);

    List<WorkOrderCost> findByWorkOrderIdIn(Collection<Long> workOrderIds);
}
