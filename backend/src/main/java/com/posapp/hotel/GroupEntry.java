package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A line on a group's master account. Amounts are always positive; the kind says which way the money goes. */
@Getter
@Setter
@Entity
@Table(name = "group_entry")
public class GroupEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HotelGroup group;

    @Enumerated(EnumType.STRING)
    private GroupEntryKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    private Booking booking;

    private Integer billNo;
    private String description;
    private BigDecimal amount = BigDecimal.ZERO;
    private String method;
    private String reference;
    private LocalDate entryDate;
}
