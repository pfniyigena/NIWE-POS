package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingNightRepository extends JpaRepository<BookingNight, Long> {

    List<BookingNight> findByBookingIdOrderByNightDateAsc(Long bookingId);

    @Modifying
    @Query("delete from BookingNight n where n.booking.id = :bookingId")
    void deleteByBookingId(@Param("bookingId") Long bookingId);
}
