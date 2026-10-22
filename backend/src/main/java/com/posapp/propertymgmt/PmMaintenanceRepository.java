package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PmMaintenanceRepository extends JpaRepository<PmMaintenance, Long> {
    boolean existsByUnitId(Long unitId);

    boolean existsByTenantId(Long tenantId);

    boolean existsByUnitIdIn(Collection<Long> unitIds);

    List<PmMaintenance> findByUnitIdInAndStatusAndCostBearerAndCompletedDateBetween(Collection<Long> unitIds, MaintenanceStatus status, CostBearer costBearer, LocalDate from, LocalDate to);
}
