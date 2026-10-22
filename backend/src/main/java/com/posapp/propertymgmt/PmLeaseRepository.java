package com.posapp.propertymgmt;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PmLeaseRepository extends JpaRepository<PmLease, Long> {
    List<PmLease> findByUnitIdOrderByStartDateDescIdDesc(Long unitId);

    List<PmLease> findByTenantIdOrderByStartDateDescIdDesc(Long tenantId);

    List<PmLease> findByUnitIdIn(Collection<Long> unitIds);

    List<PmLease> findByStatus(LeaseStatus status);

    boolean existsByUnitId(Long unitId);

    boolean existsByTenantId(Long tenantId);

    boolean existsByRenewedFromId(Long id);

    long countByUnitIdIn(Collection<Long> unitIds);

    /** The lease, locked until the end of the transaction, so two people changing its deposit at once take turns. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from PmLease l where l.id = :id")
    Optional<PmLease> lockById(@Param("id") Long id);
}
