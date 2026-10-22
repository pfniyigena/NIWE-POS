package com.posapp.booking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Something a customer can book: a consultation, a haircut, a room hire. Who performs it comes from {@link BookableServiceStaff}; the kind of room or resource it needs from {@link #resourceKind}. */
@Getter
@Setter
@Entity
public class BookableService extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private int durationMinutes = 30;

    /** Time kept free after the appointment (cleaning, notes) before the next one can start. */
    @Column(nullable = false)
    private int bufferMinutes = 0;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMode paymentMode = PaymentMode.NONE;

    /** Share of the price paid up front when the mode is DEPOSIT. */
    @Column(nullable = false)
    private int depositPercent = 30;

    /** Needs a resource of this kind (for example "Room"); blank = no room or equipment needed. */
    @Column(length = 60)
    private String resourceKind;

    @Column(length = 20)
    private String color;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean active = true;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean publicVisible = true;
}
