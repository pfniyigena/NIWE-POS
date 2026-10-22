package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingBillRepository extends JpaRepository<BookingBill, Long> {
    List<BookingBill> findByBookingIdOrderByBillNoAsc(Long bookingId);

    Optional<BookingBill> findByBookingIdAndBillNo(Long bookingId, int billNo);
}
