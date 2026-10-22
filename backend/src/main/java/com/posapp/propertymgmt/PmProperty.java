package com.posapp.propertymgmt;

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

/** A building, compound or house managed for an owner. It has one or more rentable units. */
@Getter
@Setter
@Entity
@Table(name = "pm_property", indexes = {@Index(columnList = "owner_id")})
public class PmProperty extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PmPropertyKind kind = PmPropertyKind.APARTMENT_BLOCK;

    @Column(nullable = false)
    private Long ownerId;

    @Column(length = 120)
    private String area;

    @Column(length = 500)
    private String address;

    /** The plot or title reference. */
    @Column(length = 120)
    private String titleReference;

    /** This property's management fee percentage; empty = the owner's, then the module's. */
    @Column(precision = 7, scale = 2)
    private BigDecimal feePercent;

    private LocalDate managedFrom;

    /** Active properties are managed; an archived one is kept for its history. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 2000)
    private String notes;
}
