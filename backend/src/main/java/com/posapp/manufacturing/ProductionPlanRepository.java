package com.posapp.manufacturing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Long> {

    List<ProductionPlan> findAllByOrderByCreatedAtDesc();
}
