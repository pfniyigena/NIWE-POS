package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A reservation and, once the guest arrives, the stay itself: the same record runs from BOOKED through
 * CHECKED_IN to CHECKED_OUT. Its bill (the folio) is the set of {@link FolioCharge} and {@link FolioPayment} rows
 * pointing at it.
 */
@Getter
@Setter
@Entity
@Table(name = "hotel_booking")
public class Booking extends BaseEntity {

    @Column(unique = true)
    private String bookingNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Guest guest;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private RoomType roomType;

    /** Assigned room: optional while BOOKED (can be chosen at check-in), always set once CHECKED_IN. */
    @ManyToOne(fetch = FetchType.EAGER)
    private Room room;

    private LocalDate checkInDate;
    /** Planned departure date; the night of this date is not charged. */
    private LocalDate checkOutDate;

    private Instant actualCheckIn;
    private Instant actualCheckOut;

    private int adults = 1;
    private int children = 0;

    /** Agreed room price per night before tax (defaults from the room, can be negotiated). */
    private BigDecimal nightlyRate = BigDecimal.ZERO;
    /** Snapshot of the room type's extra-person rate and included occupancy when the booking was made. */
    private BigDecimal extraPersonRate = BigDecimal.ZERO;
    private int baseOccupancy = 2;

    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.BOOKED;

    /** Walk-in, phone, website, agent... free text. */
    private String source;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String cancelReason;

    /** Booked value (before tax) currently held as unearned revenue — only used in the "booking and check-out" posting mode. */
    private BigDecimal deferredPosted = BigDecimal.ZERO;
    /** Room revenue (before tax) and tax already posted to Accounting for this stay. */
    private BigDecimal revenuePostedNet = BigDecimal.ZERO;
    private BigDecimal revenuePostedTax = BigDecimal.ZERO;

    public int guests() {
        return adults + children;
    }

    /** Nightly price including any extra-person charge, before tax. */
    public BigDecimal nightlyTotal() {
        int extra = Math.max(0, guests() - baseOccupancy);
        BigDecimal extras = extraPersonRate == null ? BigDecimal.ZERO : extraPersonRate.multiply(BigDecimal.valueOf(extra));
        return (nightlyRate == null ? BigDecimal.ZERO : nightlyRate).add(extras);
    }

    /** Booked nights (at least one, so a same-day booking still counts). */
    public long plannedNights() {
        if (checkInDate == null || checkOutDate == null) return 1;
        return Math.max(1, ChronoUnit.DAYS.between(checkInDate, checkOutDate));
    }
}
