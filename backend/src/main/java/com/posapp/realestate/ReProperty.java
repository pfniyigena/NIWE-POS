package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A property the agency has to sell or let: a house, an apartment, a plot of land, an office... */
@Getter
@Setter
@Entity
@Table(name = "re_property", indexes = {@Index(columnList = "status"), @Index(columnList = "agent_user_id")})
public class ReProperty extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropertyType type = PropertyType.HOUSE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ListingPurpose purpose = ListingPurpose.SALE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropertyStatus status = PropertyStatus.DRAFT;

    @Column(length = 4000)
    private String description;

    /** District, neighbourhood or city: what buyers search by. */
    @Column(length = 120)
    private String area;

    @Column(length = 300)
    private String address;

    /** Land title, parcel or plot reference (for example the UPI in Rwanda). */
    @Column(length = 120)
    private String titleReference;

    private Double latitude;
    private Double longitude;

    private Integer bedrooms;
    private Integer bathrooms;
    private Integer parkingSpaces;

    /** Built-up area in square metres. */
    @Column(precision = 18, scale = 2)
    private BigDecimal sizeSqm;

    /** Plot or land area in square metres. */
    @Column(precision = 18, scale = 2)
    private BigDecimal plotSqm;

    private Integer yearBuilt;
    private boolean furnished = false;

    /** Comma-separated amenities: water, power, security, garden... */
    @Column(length = 1000)
    private String features;

    @Column(precision = 18, scale = 2)
    private BigDecimal salePrice;

    /** Monthly rent. */
    @Column(precision = 18, scale = 2)
    private BigDecimal rentPrice;

    private boolean negotiable = true;
    private boolean featured = false;

    /** Shown on the public website (only while the property is also AVAILABLE and the website is switched on). */
    @Column(nullable = false)
    private boolean publicListing = false;

    private String ownerName;
    private String ownerPhone;
    private String ownerEmail;

    private Long agentUserId;
    private String agentName;

    private LocalDate listedDate;

    private String createdByName;
}
