package com.posapp.manufacturing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OverheadCostRepository extends JpaRepository<OverheadCost, Long> {

    boolean existsByCodeIgnoreCase(String code);

    List<OverheadCost> findByActiveTrueOrderByNameAsc();

    List<OverheadCost> findAllByOrderByNameAsc();
}
