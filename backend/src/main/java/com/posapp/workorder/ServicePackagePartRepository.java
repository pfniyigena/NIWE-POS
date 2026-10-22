package com.posapp.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicePackagePartRepository extends JpaRepository<ServicePackagePart, Long> {
    List<ServicePackagePart> findByPackageIdOrderByIdAsc(Long packageId);
}
