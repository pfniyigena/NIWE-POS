package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One item in the fixed-asset register. Book value = cost - {@link #accumulatedDepreciation}. Depreciation is charged
 * monthly (see {@code AssetDepreciationService}); {@link #lastDepreciatedThrough} is the month-end of the last month
 * already charged, null when nothing has been charged yet.
 */
@Getter
@Setter
@Entity
public class FixedAsset extends BaseEntity {

    /** The asset tag, e.g. FA-0001 (generated when left blank). */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AssetCategory category;

    @Column(nullable = false)
    private LocalDate purchaseDate;

    @Column(nullable = false)
    private BigDecimal cost;

    @Column(nullable = false)
    private BigDecimal salvageValue = BigDecimal.ZERO;

    @Column(nullable = false)
    private int usefulLifeMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepreciationMethod method = DepreciationMethod.STRAIGHT_LINE;

    /** Depreciation starts in this date's month (a full month is charged for it). */
    @Column(nullable = false)
    private LocalDate depreciationStartDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status = AssetStatus.ACTIVE;

    private String location;
    private String assignedTo;
    private String serialNumber;
    private String supplierName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    private LocalDate lastDepreciatedThrough;

    /** When true the first month's charge is only for the days from the start date to month end; the last month then takes what is left. */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean prorateFirstMonth = false;

    /** Set when the asset was registered from a received purchase order line (plain ids, no entity coupling to inventory). */
    private Long purchaseOrderId;
    private String purchaseOrderNumber;
    private Long purchaseOrderLineId;

    /** The branch that owns the asset (null = head office, which is what an asset registered before branches existed holds). Changes only through a transfer. */
    @Column(name = "branch_id")
    private Long branchId;

    /** True once the purchase was posted to Accounting when the asset was registered. */
    private boolean acquisitionPosted;

    private LocalDate disposalDate;
    private BigDecimal disposalProceeds;
    /** Proceeds minus book value at disposal: positive = gain, negative = loss. */
    private BigDecimal disposalGainLoss;

    @Column(columnDefinition = "TEXT")
    private String disposalNote;

    public BigDecimal bookValue() {
        return cost.subtract(accumulatedDepreciation);
    }
}
