package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawTrustTransactionRepository extends JpaRepository<LawTrustTransaction, Long> {
    List<LawTrustTransaction> findByMatterIdOrderByIdAsc(Long matterId);

    List<LawTrustTransaction> findByClientCustomerIdOrderByIdAsc(Long clientCustomerId);

    List<LawTrustTransaction> findByStatusOrderByIdAsc(TrustStatus status);

    List<LawTrustTransaction> findByInvoiceIdAndStatus(Long invoiceId, TrustStatus status);

    List<LawTrustTransaction> findAllByOrderByIdAsc();

    long countByMatterId(Long matterId);
}
