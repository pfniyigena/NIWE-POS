package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReDealRepository extends JpaRepository<ReDeal, Long> {
    List<ReDeal> findByPropertyIdOrderByIdDesc(Long propertyId);

    long countByPropertyId(Long propertyId);

    long countByLeadId(Long leadId);

    long countByPropertyIdAndStageIn(Long propertyId, Collection<DealStage> stages);

    List<ReDeal> findByPropertyIdAndStageIn(Long propertyId, Collection<DealStage> stages);

    List<ReDeal> findByStageIn(Collection<DealStage> stages);
}
