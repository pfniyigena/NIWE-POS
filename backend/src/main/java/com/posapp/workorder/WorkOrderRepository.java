package com.posapp.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    List<WorkOrder> findAllByOrderByCreatedAtDesc();

    List<WorkOrder> findByAssignedToUserIdOrderByDueDateAscCreatedAtDesc(Long userId);

    List<WorkOrder> findByAssetIdOrderByCreatedAtDesc(Long assetId);

    Optional<WorkOrder> findFirstByInvoiceId(Long invoiceId);

    Optional<WorkOrder> findFirstByNumberIgnoreCase(String number);

    long countByPackageId(Long packageId);
}
