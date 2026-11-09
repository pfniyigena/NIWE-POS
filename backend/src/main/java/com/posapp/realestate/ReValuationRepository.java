package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReValuationRepository extends JpaRepository<ReValuation, Long> {
    List<ReValuation> findByPropertyIdOrderByValuationDateDescIdDesc(Long propertyId);

    long countByPropertyId(Long propertyId);
}
