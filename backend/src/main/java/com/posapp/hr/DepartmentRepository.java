package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    java.util.List<Department> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByParentId(Long parentId);

    boolean existsByManagerId(Long managerId);
}
