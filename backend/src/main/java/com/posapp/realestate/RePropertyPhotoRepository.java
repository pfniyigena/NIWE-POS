package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RePropertyPhotoRepository extends JpaRepository<RePropertyPhoto, Long> {
    List<RePropertyPhoto> findByPropertyIdOrderBySortOrderAscIdAsc(Long propertyId);

    List<RePropertyPhoto> findByPropertyIdIn(Collection<Long> propertyIds);

    long countByPropertyId(Long propertyId);
}
