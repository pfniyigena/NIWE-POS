package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PmPropertyOwnerRepository extends JpaRepository<PmPropertyOwner, Long> {
    List<PmPropertyOwner> findByPropertyId(Long propertyId);

    boolean existsByOwnerId(Long ownerId);

    void deleteByPropertyId(Long propertyId);
}
