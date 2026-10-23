package com.posapp.manufacturing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Long> {

    List<ProductionPlan> findAllByOrderByCreatedAtDesc();

    @Query(value = "select nextval('production_plan_seq')", nativeQuery = true)
    Long nextNumber();
}
