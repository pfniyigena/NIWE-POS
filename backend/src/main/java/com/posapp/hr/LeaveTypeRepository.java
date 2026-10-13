package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    java.util.List<LeaveType> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
