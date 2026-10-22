package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PmInspectionItemRepository extends JpaRepository<PmInspectionItem, Long> {
    List<PmInspectionItem> findByInspectionIdOrderBySortOrderAscIdAsc(Long inspectionId);

    long countByInspectionId(Long inspectionId);

    void deleteByInspectionId(Long inspectionId);
}
