package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PmChargeTypeRepository extends JpaRepository<PmChargeType, Long> {
    boolean existsByNameIgnoreCase(String name);

    List<PmChargeType> findAllByOrderByNameAsc();
}
