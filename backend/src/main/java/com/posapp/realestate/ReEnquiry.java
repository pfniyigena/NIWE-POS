package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One enquiry sent from the public website. Kept so the daily limit per phone number or e-mail can be checked, and so there is a record even if the lead is later deleted. */
@Getter
@Setter
@Entity
@Table(name = "re_enquiry", indexes = @Index(columnList = "contact_key"))
public class ReEnquiry extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    private Long leadId;

    /** Digits of the phone number (last nine), or the lower-case e-mail when there is no phone. */
    @Column(name = "contact_key", nullable = false, length = 200)
    private String contactKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String message;
}
