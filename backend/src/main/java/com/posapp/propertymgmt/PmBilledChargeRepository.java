package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PmBilledChargeRepository extends JpaRepository<PmBilledCharge, Long> {
    List<PmBilledCharge> findByRentChargeIdIn(Collection<Long> rentChargeIds);

    List<PmBilledCharge> findByRentChargeIdOrderByIdAsc(Long rentChargeId);

    void deleteByRentChargeId(Long rentChargeId);

    void deleteByLeaseId(Long leaseId);
}
