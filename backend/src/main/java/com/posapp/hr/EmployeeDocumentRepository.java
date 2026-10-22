package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
    List<EmployeeDocument> findByEmployeeIdOrderByIdDesc(Long employeeId);

    List<EmployeeDocument> findByEmployeeIdAndSharedWithEmployeeTrueOrderByIdDesc(Long employeeId);

    long countByEmployeeId(Long employeeId);
}
