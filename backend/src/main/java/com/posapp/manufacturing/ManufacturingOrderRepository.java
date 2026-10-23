package com.posapp.manufacturing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ManufacturingOrderRepository extends JpaRepository<ManufacturingOrder, Long> {

    List<ManufacturingOrder> findByCreatedAtBetweenOrderByCreatedAtAscIdAsc(java.time.Instant from, java.time.Instant to);

    List<ManufacturingOrder> findAllByOrderByCreatedAtDesc();

    /** Next number for MO-0001 style order numbers (database sequence, so two people never get the same one). */
    @Query(value = "select nextval('manufacturing_order_seq')", nativeQuery = true)
    Long nextNumber();

    List<ManufacturingOrder> findByProductIdOrderByCreatedAtDesc(Long productId);
}
