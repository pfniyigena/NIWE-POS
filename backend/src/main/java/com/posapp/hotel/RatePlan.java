package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A way of selling a room (room only, bed and breakfast, corporate...): moves the nightly rate by a percent and/or an amount per night. */
@Getter
@Setter
@Entity
public class RatePlan extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** +10 for a 10% higher rate, -15 for a 15% discount. */
    private BigDecimal adjustPercent = BigDecimal.ZERO;

    /** Added to every night after the percentage (breakfast, a corporate discount as a negative number...). */
    private BigDecimal adjustAmount = BigDecimal.ZERO;

    /** The plan cannot be sold for fewer nights than this. */
    private int minNights = 1;

    private boolean active = true;
}
