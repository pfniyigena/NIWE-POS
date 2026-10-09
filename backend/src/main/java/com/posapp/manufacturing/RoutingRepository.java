package com.posapp.manufacturing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutingRepository extends JpaRepository<Routing, Long> {

    List<Routing> findByActiveTrueOrderByNameAsc();

    List<Routing> findAllByOrderByNameAsc();
}
