package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PmFeeInvoiceRepository extends JpaRepository<PmFeeInvoice, Long> {
    Optional<PmFeeInvoice> findByStatementIdAndStatus(Long statementId, PmFeeInvoiceStatus status);

    List<PmFeeInvoice> findByStatementIdOrderByIdDesc(Long statementId);
}
