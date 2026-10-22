package com.posapp.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {
    List<ServicePackage> findAllByOrderByNameAsc();

    List<ServicePackage> findByActiveTrueOrderByNameAsc();
}
