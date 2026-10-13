package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
}
