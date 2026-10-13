package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A kind of fixed asset (Computers, Vehicles, Furniture...). Holds the depreciation defaults every new asset of that
 * kind starts with; each asset keeps its own copy, so changing a category later never rewrites existing assets.
 */
@Getter
@Setter
@Entity
public class AssetCategory extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepreciationMethod method = DepreciationMethod.STRAIGHT_LINE;

    @Column(nullable = false)
    private int usefulLifeMonths = 60;

    /** The value left at the end of the useful life, as a percentage of cost (0 = depreciates to nothing). */
    @Column(nullable = false)
    private BigDecimal salvagePercent = BigDecimal.ZERO;

    /** New assets of this category charge only the days left in their first month (see FixedAsset.prorateFirstMonth). */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean prorateFirstMonth = false;

    private boolean active = true;
}
