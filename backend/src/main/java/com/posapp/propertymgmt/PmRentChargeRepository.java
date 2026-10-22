package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PmRentChargeRepository extends JpaRepository<PmRentCharge, Long> {
    List<PmRentCharge> findByLeaseIdOrderByPeriodStartAsc(Long leaseId);

    List<PmRentCharge> findByLeaseIdIn(Collection<Long> leaseIds);

    List<PmRentCharge> findByStatus(RentChargeStatus status);

    List<PmRentCharge> findByInvoiceIdIn(Collection<Long> invoiceIds);

    boolean existsByLeaseId(Long leaseId);
}
