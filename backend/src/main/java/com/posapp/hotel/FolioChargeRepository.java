package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FolioChargeRepository extends JpaRepository<FolioCharge, Long> {
    List<FolioCharge> findByBookingIdOrderByIdAsc(Long bookingId);

    /** Includes voided lines on purpose: a night that was voided (a comped night) must not be charged again by the next audit. */
    boolean existsByBookingIdAndChargeTypeAndNightDate(Long bookingId, ChargeType chargeType, LocalDate nightDate);

    List<FolioCharge> findByChargeTypeAndNightDateBetweenAndVoidedFalse(ChargeType chargeType, LocalDate from, LocalDate to);
}
