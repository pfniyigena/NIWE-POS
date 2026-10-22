package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PmDepositTxnRepository extends JpaRepository<PmDepositTxn, Long> {
    List<PmDepositTxn> findByLeaseIdOrderByTxnDateAscIdAsc(Long leaseId);

    boolean existsByLeaseId(Long leaseId);
}
