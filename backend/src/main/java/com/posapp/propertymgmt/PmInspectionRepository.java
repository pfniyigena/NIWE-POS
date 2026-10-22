package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PmInspectionRepository extends JpaRepository<PmInspection, Long> {
    List<PmInspection> findByLeaseIdOrderByInspectionDateDescIdDesc(Long leaseId);

    boolean existsByLeaseIdAndKind(Long leaseId, PmInspectionKind kind);

    boolean existsByLeaseId(Long leaseId);
}
