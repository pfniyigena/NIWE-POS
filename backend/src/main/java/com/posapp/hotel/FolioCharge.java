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
import java.time.LocalDate;

/** One line on a guest's bill. amount = net + tax. Voided lines stay for the record but no longer count. */
@Getter
@Setter
@Entity
public class FolioCharge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    private ChargeType chargeType;

    private String description;

    /** For ROOM lines: the night this line pays for (one line per night). */
    private LocalDate nightDate;

    private BigDecimal net = BigDecimal.ZERO;
    private BigDecimal tax = BigDecimal.ZERO;
    private BigDecimal amount = BigDecimal.ZERO;

    /** RESTAURANT_ORDER / POS_ORDER when the line came from another module. */
    private String sourceType;
    private Long sourceId;

    /** True once this line's revenue has been posted to Accounting by the hotel itself (EXTRA lines). */
    @Column(name = "revenue_posted")
    private boolean revenuePosted = false;

    private boolean voided = false;
    private String voidReason;
}
