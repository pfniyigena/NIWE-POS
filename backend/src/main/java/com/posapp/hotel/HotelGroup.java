package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import com.posapp.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A group of guests booked together (a wedding, a conference, a tour): shared dates, held rooms, a rooming list and optionally one account. */
@Getter
@Setter
@Entity
@Table(name = "hotel_group")
public class HotelGroup extends BaseEntity {

    @Column(unique = true)
    private String groupCode;

    @Column(nullable = false)
    private String name;

    private String contactName;
    private String contactPhone;
    private String contactEmail;

    /** The placeholder guest that unnamed rooms of the rooming list are booked under until a name is given. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Guest contactGuest;

    /** Who the master invoice is addressed to (optional). */
    @ManyToOne(fetch = FetchType.EAGER)
    private Customer company;

    private LocalDate arrivalDate;
    private LocalDate departureDate;

    @Enumerated(EnumType.STRING)
    private GroupStatus status = GroupStatus.TENTATIVE;

    @Enumerated(EnumType.STRING)
    private GroupBilling billing = GroupBilling.INDIVIDUAL;

    @Column(columnDefinition = "text")
    private String notes;
}
