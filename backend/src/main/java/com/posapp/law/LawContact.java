package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/**
 * Someone or something that appears on matters other than our own clients: the opposing party, their lawyer, a witness, an expert, a
 * court official. Our clients are customers. The conflict check searches both.
 */
@Getter
@Setter
@Entity
public class LawContact extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactType type = ContactType.PERSON;

    @Column(length = 60)
    private String phone;

    private String email;

    /** National ID, passport or company registration number. */
    @Column(length = 100)
    private String idNumber;

    @Column(length = 500)
    private String address;

    @Column(length = 2000)
    private String notes;

    private String createdByName;
}
