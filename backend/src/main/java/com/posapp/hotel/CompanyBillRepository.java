package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface CompanyBillRepository extends JpaRepository<CompanyBill, Long>, JpaSpecificationExecutor<CompanyBill> {
    List<CompanyBill> findByBookingIdOrderByBillNoAsc(Long bookingId);

    @Query("select coalesce(sum(b.amount - b.paid), 0) from CompanyBill b where b.company.id = :companyId")
    BigDecimal outstandingFor(Long companyId);

    @Query("select coalesce(sum(b.amount - b.paid), 0) from CompanyBill b")
    BigDecimal outstandingTotal();
}
