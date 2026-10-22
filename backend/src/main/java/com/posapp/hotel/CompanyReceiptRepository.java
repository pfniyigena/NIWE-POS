package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyReceiptRepository extends JpaRepository<CompanyReceipt, Long> {
    List<CompanyReceipt> findByCompanyBillIdOrderByIdAsc(Long companyBillId);

    List<CompanyReceipt> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(java.time.Instant from, java.time.Instant to);
}
