package com.posapp.quotation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    List<Quotation> findAllByOrderByQuoteDateDesc();

    /** Guards a delete-vs-deactivate-style decision for Customer, mirroring SalesOrderRepository.existsByCustomerId — unused today, kept for symmetry. */
    boolean existsByCustomerId(Long customerId);

    /**
     * Backs {@code QuotationService}'s daily expiry-warning sweep (Fourier: "Wiring a handful of
     * business events to that same mailer") — every still-open (SENT) quote whose {@code
     * validUntil} falls on or before the cutoff date (today + the configured warning-days) and
     * hasn't already been alerted on. DRAFT/ACCEPTED/REJECTED/EXPIRED/CONVERTED quotes are never
     * matched: only a SENT one is still an open offer actually waiting on a customer to answer.
     */
    List<Quotation> findByStatusAndValidUntilLessThanEqualAndExpiryAlertSentFalse(QuotationStatus status, LocalDate cutoff);
}
