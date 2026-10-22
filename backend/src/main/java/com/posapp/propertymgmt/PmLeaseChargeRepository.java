package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PmLeaseChargeRepository extends JpaRepository<PmLeaseCharge, Long> {
    List<PmLeaseCharge> findByLeaseIdOrderByIdAsc(Long leaseId);

    List<PmLeaseCharge> findByLeaseIdIn(Collection<Long> leaseIds);

    boolean existsByChargeTypeId(Long chargeTypeId);

    void deleteByLeaseId(Long leaseId);
}
