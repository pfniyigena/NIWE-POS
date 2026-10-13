package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Bookings that hold a room type on at least one night of [from, to) — BOOKED or CHECKED_IN, optionally excluding one booking (when editing it). */
    @Query("select b from Booking b where b.status in :statuses and b.checkInDate < :to and (b.checkOutDate > :from or b.status = com.posapp.hotel.BookingStatus.CHECKED_IN)")
    List<Booking> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                  @Param("statuses") Collection<BookingStatus> statuses);

    List<Booking> findByStatusOrderByCheckInDateAscIdAsc(BookingStatus status);

    List<Booking> findByStatusAndCheckInDateOrderByIdAsc(BookingStatus status, LocalDate checkInDate);

    List<Booking> findByStatusAndCheckOutDateLessThanEqualOrderByCheckOutDateAscIdAsc(BookingStatus status, LocalDate checkOutDate);

    List<Booking> findByStatusAndRoomId(BookingStatus status, Long roomId);

    long countByGuestId(Long guestId);

    long countByRoomTypeId(Long roomTypeId);

    long countByRoomId(Long roomId);

    /** Bookings whose stay touches [from, to]; used by the reports. */
    @Query("select b from Booking b where b.status in :statuses and b.checkInDate <= :to and b.checkOutDate >= :from")
    List<Booking> findTouching(@Param("from") LocalDate from, @Param("to") LocalDate to,
                               @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b left join b.room r where lower(b.bookingNumber) like lower(concat('%', :q, '%')) "
            + "or lower(b.guest.fullName) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(b.guest.phone, '')) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(r.number, '')) like lower(concat('%', :q, '%')) order by b.checkInDate desc, b.id desc")
    List<Booking> search(@Param("q") String q, org.springframework.data.domain.Pageable pageable);

    List<Booking> findAllByOrderByCheckInDateDescIdDesc(org.springframework.data.domain.Pageable pageable);

    List<Booking> findByStatusOrderByCheckInDateDescIdDesc(BookingStatus status, org.springframework.data.domain.Pageable pageable);
}
