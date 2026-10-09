package com.posapp.branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);

    List<Branch> findAllByOrderByNameAsc();

    List<Branch> findByActiveTrueOrderByNameAsc();

    /** Zero rows before the startup seed runs once, exactly one afterwards. */
    Optional<Branch> findByHeadquartersTrue();
}
