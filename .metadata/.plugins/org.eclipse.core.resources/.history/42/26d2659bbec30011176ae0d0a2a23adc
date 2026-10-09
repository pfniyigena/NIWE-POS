package com.posapp.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseLocationRepository extends JpaRepository<WarehouseLocation, Long> {

    boolean existsByWarehouseAndNameIgnoreCase(Warehouse warehouse, String name);

    List<WarehouseLocation> findAllByWarehouseOrderByNameAsc(Warehouse warehouse);

    /** Should only ever match zero (before migration/seeding runs) or exactly one row per warehouse — see LocationType's own javadoc on the "exactly one RECEIVING" invariant. */
    Optional<WarehouseLocation> findByWarehouseAndType(Warehouse warehouse, LocationType type);

    /** Every row of a given type for a warehouse, active or not — used by migration/backfill and by invariant checks that need to see inactive rows too. */
    List<WarehouseLocation> findAllByWarehouseAndType(Warehouse warehouse, LocationType type);

    /** Backs "at least one active SALES location" checks (and the equivalent for any other type) without throwing. */
    List<WarehouseLocation> findAllByWarehouseAndTypeAndActiveTrue(Warehouse warehouse, LocationType type);
}
