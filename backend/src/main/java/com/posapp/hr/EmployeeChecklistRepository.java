package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeChecklistRepository extends JpaRepository<EmployeeChecklist, Long> {
    List<EmployeeChecklist> findByEmployeeIdOrderByIdDesc(Long employeeId);

    List<EmployeeChecklist> findAllByOrderByIdDesc();
}
