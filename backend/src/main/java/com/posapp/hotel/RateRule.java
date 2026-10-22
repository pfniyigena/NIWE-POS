package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * A season, weekend or long-stay price. It covers the nights from {@code startDate} to {@code endDate} (both included, open-ended when
 * empty), optionally only on some weekdays, only for one room type, and only for stays of at least {@code minNights}.
 * When several rules cover a night the highest priority wins; at equal priority a rule for one room type beats "all room types",
 * then the one that starts later.
 */
@Getter
@Setter
@Entity
public class RateRule extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** Empty = every room type. */
    @ManyToOne(fetch = FetchType.EAGER)
    private RoomType roomType;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    /** Comma separated MON,TUE,... — empty = every day. */
    private String daysOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RateRuleMode mode = RateRuleMode.FIXED;

    /** FIXED: the nightly rate. PERCENT: +/- percent of the base rate. AMOUNT: +/- amount per night. */
    @Column(name = "rule_value", nullable = false)
    private BigDecimal value = BigDecimal.ZERO;

    private int minNights = 1;

    private int priority = 0;

    private boolean active = true;

    public boolean coversDay(LocalDate night) {
        if (night.isBefore(startDate)) return false;
        if (endDate != null && night.isAfter(endDate)) return false;
        if (daysOfWeek == null || daysOfWeek.isBlank()) return true;
        DayOfWeek dow = night.getDayOfWeek();
        String code = dow.name().substring(0, 3);
        for (String d : daysOfWeek.split(",")) {
            if (d.trim().equalsIgnoreCase(code)) return true;
        }
        return false;
    }
}
