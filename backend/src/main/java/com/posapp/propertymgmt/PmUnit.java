package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A rentable unit of a managed property: a flat, a room, a shop, or a whole house. */
@Getter
@Setter
@Entity
@Table(name = "pm_unit", indexes = {@Index(columnList = "property_id")}, uniqueConstraints = {@UniqueConstraint(columnNames = {"property_id", "unit_no"})})
public class PmUnit extends BaseEntity {

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false, length = 40)
    private String unitNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PmUnitKind kind = PmUnitKind.APARTMENT;

    @Column(length = 40)
    private String floor;

    private Integer bedrooms;

    private Integer bathrooms;

    @Column(precision = 12, scale = 2)
    private BigDecimal sizeSqm;

    /** The rent we ask for it, per month. */
    @Column(precision = 18, scale = 2)
    private BigDecimal marketRent;

    /** The deposit a new lease starts with. */
    @Column(precision = 18, scale = 2)
    private BigDecimal defaultDeposit;

    /** Not to be let for now (under repair, kept by the owner...). */
    @Column(nullable = false)
    private boolean offMarket = false;

    @Column(length = 500)
    private String offMarketNote;

    @Column(length = 1000)
    private String notes;
}
