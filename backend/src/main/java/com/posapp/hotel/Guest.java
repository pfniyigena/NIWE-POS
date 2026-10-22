package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** A person who stays (or books to stay). Kept between visits so a returning guest is found, not retyped. */
@Getter
@Setter
@Entity
public class Guest extends BaseEntity {

    @Column(nullable = false)
    private String fullName;

    private String phone;
    private String email;
    private String nationality;
    /** Passport, national ID, driving licence... */
    private String idType;
    private String idNumber;
    private String address;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** A guest the hotel wants to look after. */
    private boolean vip = false;

    /** A guest the hotel will not take: new bookings and check-in are refused while this is set. */
    private boolean blacklisted = false;
    private String blacklistReason;

    /** Pillow, floor, diet, late arrival... shown on the booking and the arrivals list. */
    @Column(columnDefinition = "TEXT")
    private String preferences;

    private LocalDate dateOfBirth;
    private String profession;
}
