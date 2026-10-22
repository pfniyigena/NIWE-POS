package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReValuationRepository extends JpaRepository<ReValuation, Long> {
    List<ReValuation> findByPropertyIdOrderByValuationDateDescIdDesc(Long propertyId);

    long countByPropertyId(Long propertyId);
}
