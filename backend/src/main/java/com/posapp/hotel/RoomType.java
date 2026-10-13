package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A kind of room (Standard, Deluxe, Suite...) with one nightly rate. */
@Getter
@Setter
@Entity
public class RoomType extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Nightly rate for the room itself, before tax. */
    @Column(nullable = false)
    private BigDecimal baseRate = BigDecimal.ZERO;

    /** Guests included in the base rate; each guest above this adds {@link #extraPersonRate} per night. */
    private int baseOccupancy = 2;

    /** Most guests the room sleeps. */
    private int maxOccupancy = 2;

    private BigDecimal extraPersonRate = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String amenities;

    private boolean active = true;
}
