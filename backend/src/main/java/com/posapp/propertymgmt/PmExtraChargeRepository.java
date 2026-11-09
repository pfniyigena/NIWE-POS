package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PmExtraChargeRepository extends JpaRepository<PmExtraCharge, Long> {
    List<PmExtraCharge> findByLeaseIdOrderByChargeDateDescIdDesc(Long leaseId);

    List<PmExtraCharge> findByLeaseIdAndStatusOrderByChargeDateAscIdAsc(Long leaseId, PmExtraStatus status);

    List<PmExtraCharge> findByRentChargeIdAndStatus(Long rentChargeId, PmExtraStatus status);

    List<PmExtraCharge> findByLeaseChargeIdOrderByIdAsc(Long leaseChargeId);

    boolean existsByLeaseId(Long leaseId);

    boolean existsByChargeTypeId(Long chargeTypeId);

    void deleteByLeaseId(Long leaseId);
}
