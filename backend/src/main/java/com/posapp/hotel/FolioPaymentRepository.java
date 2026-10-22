package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolioPaymentRepository extends JpaRepository<FolioPayment, Long> {
    List<FolioPayment> findByBookingIdOrderByIdAsc(Long bookingId);

    List<FolioPayment> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(java.time.Instant from, java.time.Instant to);

    /** Money on stays that are still open (booked or in the house): kind, amount. */
    @org.springframework.data.jpa.repository.Query("select p.kind, coalesce(sum(p.amount), 0) from FolioPayment p where p.booking.status in :statuses group by p.kind")
    List<Object[]> totalsByKindForStatuses(@org.springframework.data.repository.query.Param("statuses") java.util.Collection<BookingStatus> statuses);
}
