package com.posapp.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicePackageItemRepository extends JpaRepository<ServicePackageItem, Long> {
    List<ServicePackageItem> findByPackageIdOrderBySeqAsc(Long packageId);
}
