package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    java.util.List<Employee> findAllByOrderByFirstNameAscLastNameAsc();

    boolean existsByEmployeeNumber(String employeeNumber);

    boolean existsByDepartmentId(Long departmentId);

    boolean existsByPositionId(Long positionId);

    java.util.Optional<Employee> findFirstByUserId(Long userId);

    long countByDepartmentId(Long departmentId);

    boolean existsByManagerId(Long managerId);

    long countByPositionIdAndStatus(Long positionId, EmployeeStatus status);
}
