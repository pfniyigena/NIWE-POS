package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawBillRepository extends JpaRepository<LawBill, Long> {
    List<LawBill> findByMatterIdOrderByIdDesc(Long matterId);

    List<LawBill> findByStatus(BillStatus status);

    long countByMatterId(Long matterId);
}
