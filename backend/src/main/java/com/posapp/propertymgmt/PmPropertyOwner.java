package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One owner's share of a property in one split. The rows with the same property and {@code validFrom} make a split and add up to 100%. A split holds from its date until the next split
 * of the property starts; an empty date means from the beginning.
 */
@Getter
@Setter
@Entity
@Table(name = "pm_property_owner", indexes = {@Index(columnList = "property_id"), @Index(columnList = "owner_id")})
public class PmPropertyOwner extends BaseEntity {

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal sharePercent;

    private LocalDate validFrom;

    /** The owner the agency deals with: tenants' and maintenance mail goes to them, and rounding cents are theirs. One per split. */
    @Column(nullable = false)
    private boolean leadOwner;
}
