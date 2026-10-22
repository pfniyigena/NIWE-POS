package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PmFeeInvoiceLineRepository extends JpaRepository<PmFeeInvoiceLine, Long> {
    List<PmFeeInvoiceLine> findByInvoiceIdOrderBySortOrderAscIdAsc(Long invoiceId);

    List<PmFeeInvoiceLine> findByInvoiceIdIn(Collection<Long> invoiceIds);
}
