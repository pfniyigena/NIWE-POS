package com.posapp.manufacturing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManufacturingOrderRepository extends JpaRepository<ManufacturingOrder, Long> {

    List<ManufacturingOrder> findAllByOrderByCreatedAtDesc();

    List<ManufacturingOrder> findByProductIdOrderByCreatedAtDesc(Long productId);
}
