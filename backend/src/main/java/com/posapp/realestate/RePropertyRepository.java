package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RePropertyRepository extends JpaRepository<ReProperty, Long> {
    List<ReProperty> findAllByOrderByIdDesc();

    List<ReProperty> findByStatus(PropertyStatus status);
}
