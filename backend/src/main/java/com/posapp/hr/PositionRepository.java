package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long> {
    java.util.List<Position> findAllByOrderByTitleAsc();

    boolean existsByDepartmentId(Long departmentId);
}
