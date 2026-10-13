package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    java.util.List<Contract> findAllByOrderByStartDateDescIdDesc();

    java.util.List<Contract> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    java.util.List<Contract> findByStatus(ContractStatus status);

    boolean existsByContractNumber(String contractNumber);
}
