package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReUnitSaleRepository extends JpaRepository<ReUnitSale, Long> {
    List<ReUnitSale> findByUnitIdOrderByIdDesc(Long unitId);

    List<ReUnitSale> findByProjectId(Long projectId);

    long countByUnitId(Long unitId);

    long countByProjectId(Long projectId);

    long countByLeadId(Long leadId);

    List<ReUnitSale> findByUnitIdAndStageIn(Long unitId, Collection<UnitSaleStage> stages);

    List<ReUnitSale> findByStageIn(Collection<UnitSaleStage> stages);
}
