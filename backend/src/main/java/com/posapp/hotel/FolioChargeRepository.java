package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FolioChargeRepository extends JpaRepository<FolioCharge, Long> {
    List<FolioCharge> findByBookingIdOrderByIdAsc(Long bookingId);

    /** Includes voided lines on purpose: a night that was voided (a comped night) must not be charged again by the next audit. */
    boolean existsByBookingIdAndChargeTypeAndNightDate(Long bookingId, ChargeType chargeType, LocalDate nightDate);

    List<FolioCharge> findByChargeTypeAndNightDateBetweenAndVoidedFalse(ChargeType chargeType, LocalDate from, LocalDate to);

    /** What each stay was charged (voided lines left out): booking id, amount including tax. */
    @Query("select c.booking.id, coalesce(sum(c.amount), 0) from FolioCharge c where c.booking.id in :ids and c.voided = false group by c.booking.id")
    List<Object[]> totalsByBooking(@Param("ids") Collection<Long> ids);

    List<FolioCharge> findByBookingIdIn(Collection<Long> bookingIds);

    List<FolioCharge> findByVoidedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(java.time.Instant from, java.time.Instant to);

    /** Net and tax of the extra / fee lines the hotel itself posted to Accounting (voided lines had theirs reversed and are left out). */
    @Query("select coalesce(sum(c.net), 0), coalesce(sum(c.tax), 0) from FolioCharge c where c.voided = false and c.revenuePosted = true and c.chargeType in (com.posapp.hotel.ChargeType.EXTRA, com.posapp.hotel.ChargeType.FEE)")
    List<Object[]> postedExtrasTotals();
}
