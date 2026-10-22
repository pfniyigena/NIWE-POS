package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PmUnitRepository extends JpaRepository<PmUnit, Long> {
    List<PmUnit> findByPropertyIdOrderByUnitNoAsc(Long propertyId);

    List<PmUnit> findByPropertyIdIn(Collection<Long> propertyIds);

    boolean existsByPropertyIdAndUnitNoIgnoreCase(Long propertyId, String unitNo);

    long countByPropertyId(Long propertyId);
}
