package com.posapp.manufacturing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkstationRepository extends JpaRepository<Workstation, Long> {

    boolean existsByCodeIgnoreCase(String code);

    List<Workstation> findByActiveTrueOrderByNameAsc();

    List<Workstation> findAllByOrderByNameAsc();
}
