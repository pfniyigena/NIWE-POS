package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Booking> {

    /** Bookings that hold a room type on at least one night of [from, to) — BOOKED or CHECKED_IN, optionally excluding one booking (when editing it). */
    @Query("select b from Booking b where b.status in :statuses and b.checkInDate < :to and (b.checkOutDate > :from or b.status = com.posapp.hotel.BookingStatus.CHECKED_IN)")
    List<Booking> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                  @Param("statuses") Collection<BookingStatus> statuses);

    List<Booking> findByStatusOrderByCheckInDateAscIdAsc(BookingStatus status);

    List<Booking> findByStatusAndCheckInDateOrderByIdAsc(BookingStatus status, LocalDate checkInDate);

    List<Booking> findByStatusAndCheckOutDateLessThanEqualOrderByCheckOutDateAscIdAsc(BookingStatus status, LocalDate checkOutDate);

    List<Booking> findByStatusAndRoomId(BookingStatus status, Long roomId);

    List<Booking> findByGroupIdOrderByIdAsc(Long groupId);

    /** The latest stay in a room that started on or before a date (to find whose property was left behind). */
    java.util.Optional<Booking> findFirstByRoomIdAndStatusInAndCheckInDateLessThanEqualOrderByCheckInDateDescIdDesc(Long roomId, Collection<BookingStatus> statuses, LocalDate date);

    /** Bookings with a room assigned that stay on a date (arrivals not yet checked in). */
    List<Booking> findByStatusAndCheckInDateAndRoomIsNotNull(BookingStatus status, LocalDate date);

    long countByGroupId(Long groupId);

    long countByGuestId(Long guestId);

    List<Booking> findByGuestIdOrderByCheckInDateDescIdDesc(Long guestId);

    long countByRoomTypeId(Long roomTypeId);

    long countByRoomId(Long roomId);

    long countByRatePlanId(Long ratePlanId);

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking b set b.guest = :to where b.guest.id = :from")
    int moveGuest(@Param("from") Long from, @Param("to") Guest to);

    List<Booking> findByCheckInDateBetween(LocalDate from, LocalDate to);

    /** Room revenue the hotel says it has posted to Accounting (net, tax) across all stays. */
    @Query("select coalesce(sum(b.revenuePostedNet), 0), coalesce(sum(b.revenuePostedTax), 0) from Booking b")
    List<Object[]> postedRoomTotals();

    List<Booking> findByActualCheckInGreaterThanEqualAndActualCheckInLessThan(java.time.Instant from, java.time.Instant to);

    List<Booking> findByActualCheckOutGreaterThanEqualAndActualCheckOutLessThan(java.time.Instant from, java.time.Instant to);

    // ---- online booking and guest e-mails (HO-8)

    java.util.Optional<Booking> findByConfirmationCode(String confirmationCode);

    long countByOnlineTrueAndGuestPhoneAndCreatedAtGreaterThanEqual(String phone, java.time.Instant since);

    long countByOnlineTrueAndCreatedAtGreaterThanEqual(java.time.Instant since);

    List<Booking> findByOnlineTrueAndStatusAndHoldExpiresAtNotNull(BookingStatus status);

    List<Booking> findByStatusAndCheckInDateAndPrearrivalMailedAtIsNull(BookingStatus status, LocalDate checkInDate);

    List<Booking> findByStatusAndActualCheckOutGreaterThanEqualAndThanksMailedAtIsNull(BookingStatus status, java.time.Instant since);

    List<Booking> findByStatusAndConfirmationMailedAtIsNullAndCreatedAtGreaterThanEqual(BookingStatus status, java.time.Instant since);
}
