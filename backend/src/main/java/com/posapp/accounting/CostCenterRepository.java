package com.posapp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CostCenterRepository extends JpaRepository<CostCenter, Long> {

    List<CostCenter> findAllByOrderByCodeAsc();

    List<CostCenter> findByActiveTrueOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);
}
