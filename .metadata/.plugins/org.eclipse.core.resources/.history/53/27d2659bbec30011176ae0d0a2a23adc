package com.posapp.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<Warehouse> findAllByOrderByNameAsc();

    List<Warehouse> findByActiveTrueOrderByNameAsc();

    /** Should only ever match zero (before the startup seed runs once) or exactly one row — see Warehouse.primary's javadoc. */
    Optional<Warehouse> findByPrimaryTrue();
}
