package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The room price of one night of an AUTO-priced booking (before tax and before any extra-guest charge). */
@Getter
@Setter
@Entity
@Table(name = "hotel_booking_night")
public class BookingNight extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Booking booking;

    @Column(nullable = false)
    private LocalDate nightDate;

    @Column(nullable = false)
    private BigDecimal rate = BigDecimal.ZERO;

    /** Which rule set the price, for the bill and the quote ("High season"). */
    private String ruleName;
}
