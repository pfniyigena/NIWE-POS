package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PmLateFeeRepository extends JpaRepository<PmLateFee, Long> {
    List<PmLateFee> findByRentChargeIdIn(Collection<Long> rentChargeIds);

    Optional<PmLateFee> findByRentChargeId(Long rentChargeId);
}
