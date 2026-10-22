package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PmPropertyRepository extends JpaRepository<PmProperty, Long> {
    List<PmProperty> findByOwnerId(Long ownerId);

    long countByOwnerId(Long ownerId);
}
