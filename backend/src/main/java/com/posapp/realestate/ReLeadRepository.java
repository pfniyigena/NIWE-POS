package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReLeadRepository extends JpaRepository<ReLead, Long> {
    List<ReLead> findAllByOrderByIdDesc();

    List<ReLead> findByPhoneKey(String phoneKey);

    long countByPropertyId(Long propertyId);
}
