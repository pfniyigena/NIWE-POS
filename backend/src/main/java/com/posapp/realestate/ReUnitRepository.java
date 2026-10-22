package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReUnitRepository extends JpaRepository<ReUnit, Long> {
    List<ReUnit> findByProjectIdOrderByBlockAscUnitNoAsc(Long projectId);

    List<ReUnit> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    boolean existsByProjectIdAndUnitNoIgnoreCase(Long projectId, String unitNo);
}
