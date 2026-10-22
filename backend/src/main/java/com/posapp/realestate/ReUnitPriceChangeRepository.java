package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReUnitPriceChangeRepository extends JpaRepository<ReUnitPriceChange, Long> {
    List<ReUnitPriceChange> findByUnitIdOrderByIdDesc(Long unitId);

    void deleteByUnitId(Long unitId);
}
