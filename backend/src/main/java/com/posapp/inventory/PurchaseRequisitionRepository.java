package com.posapp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {

    List<PurchaseRequisition> findAllByOrderByRequestDateDesc();

    List<PurchaseRequisition> findByStatusIn(java.util.Collection<PurchaseRequisitionStatus> statuses);
}
